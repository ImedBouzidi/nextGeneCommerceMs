import os
import json
import requests
import threading
import numpy as np
import pandas as pd
from sklearn.metrics.pairwise import cosine_similarity
from sqlalchemy import create_engine, text


class Recommender:
    """Recommender that can load product vectors from a JSON file or from a database.

    It uses weighted-average of product vectors (weights = ratings) to build a
    user profile and returns nearest products by cosine similarity.
    """

    def __init__(self, products_path=None, db_url=None, catalogue_url=None):
        self.products = []
        self.ids = []
        self.vectors = np.empty((0, 0))
        self.ratings_df = pd.DataFrame()
        self.lock = threading.RLock()

        # Priority: catalogue service > database URL > local file
        self.catalogue_url = catalogue_url or os.environ.get('CATALOGUE_URL')
        if self.catalogue_url:
            try:
                self._load_from_catalogue(self.catalogue_url)
                return
            except Exception:
                # fall through to DB / file fallback
                pass

        # Prefer DB if DATABASE_URL or db_url is set
        self.db_url = db_url or os.environ.get('DATABASE_URL')
        if self.db_url:
            try:
                self._load_from_db()
                return
            except Exception:
                # Fall back to files
                pass

        # Fallback: load from products_path JSON file
        products_path = products_path or 'recommender-service/data/products.json'
        with open(products_path, 'r') as f:
            self.products = json.load(f)

        self.ids = [p['id'] for p in self.products]
        # ensure fixed-length vectors
        self.vectors = np.array([p.get('vector', [0] * 10) for p in self.products])

    def _load_from_db(self):
        engine = create_engine(self.db_url)
        # Attempt to read products table: expects columns (id, vector)
        with engine.connect() as conn:
            # products
            try:
                prod_df = pd.read_sql(text('SELECT id, vector FROM products'), conn)
            except Exception:
                # If table/column names differ, raise to fallback
                raise

            # Try to parse vector column which can be stored as JSON/text
            def parse_vec(x):
                if x is None:
                    return None
                if isinstance(x, (list, tuple)):
                    return list(x)
                try:
                    return json.loads(x)
                except Exception:
                    try:
                        # comma separated numbers
                        return [float(v) for v in str(x).split(',')]
                    except Exception:
                        return None

            prod_df['vector_parsed'] = prod_df['vector'].apply(parse_vec)
            prod_df = prod_df[prod_df['vector_parsed'].notnull()]
            products_local = prod_df.to_dict(orient='records')
            ids_local = [str(p['id']) for p in products_local]
            vectors_local = np.array([p['vector_parsed'] for p in products_local])

            # ratings: expects (user_id, product_id, rating)
            try:
                ratings_local = pd.read_sql(text('SELECT user_id, product_id, rating FROM ratings'), conn)
            except Exception:
                ratings_local = pd.DataFrame()

            # assign atomically
            with self.lock:
                self.products = products_local
                self.ids = ids_local
                self.vectors = vectors_local
                self.ratings_df = ratings_local

        def _load_from_catalogue(self, base_url):
            base = base_url.rstrip('/')
            # fetch products
            resp = requests.get(f"{base}/api/products", timeout=5)
            resp.raise_for_status()
            products = resp.json()

            products_local = []
            ratings_local = []
            for p in products:
                pid = str(p.get('id'))
                entry = {'id': pid}
                if 'vector' in p and p.get('vector'):
                    entry['vector'] = p.get('vector')
                products_local.append(entry)

                # fetch reviews for product
                try:
                    r = requests.get(f"{base}/api/reviews/product/{pid}", timeout=5)
                    if r.status_code == 200:
                        revs = r.json()
                        for rev in revs:
                            try:
                                reviewer = rev.get('reviewerId') or rev.get('reviewer_id') or rev.get('reviewer') or rev.get('reviewerId')
                                ratings_local.append({
                                    'user_id': str(reviewer),
                                    'product_id': pid,
                                    'rating': float(rev.get('rating'))
                                })
                            except Exception:
                                continue
                except Exception:
                    # ignore missing reviews for a product
                    pass

            ids_local = [p['id'] for p in products_local]
            vectors_local = np.array([p.get('vector', [0] * 10) for p in products_local]) if any('vector' in p for p in products_local) else np.empty((0, 0))
            ratings_df_local = pd.DataFrame(ratings_local) if ratings_local else pd.DataFrame()

            # atomically update state
            with self.lock:
                self.products = products_local
                self.ids = ids_local
                self.vectors = vectors_local
                self.ratings_df = ratings_df_local

                # If we don't have vectors but have ratings, precompute item-item similarity
                if self.vectors.size == 0 and not self.ratings_df.empty:
                    pivot = self.ratings_df.pivot_table(index='product_id', columns='user_id', values='rating', fill_value=0)
                    self.similarity_matrix = cosine_similarity(pivot.values)
                    self.sim_items = list(pivot.index)
                else:
                    self.similarity_matrix = None
                    self.sim_items = None

    def recommend(self, user_history=None, n=5, user_id=None):
        """Return top-n product ids.

        - If `user_id` is provided and ratings are available in DB, build profile
          from that user's ratings (weighted average of product vectors by rating).
        - Else if `user_history` is provided (list of product ids), average their
          vectors (unweighted) to make the profile.
        - Else return top-n by popularity (if ratings exist) or first n products.
        """
        if len(self.ids) == 0:
            return []

        # If we have product vectors -> content-based (embedding) approach
        if getattr(self, 'vectors', None) is not None and getattr(self, 'vectors').size > 0:
            # Build user vector from ratings if available
            user_vec = None
            if user_id is not None and not self.ratings_df.empty:
                df = self.ratings_df[self.ratings_df['user_id'] == user_id]
                if not df.empty:
                    rows = []
                    weights = []
                    for _, r in df.iterrows():
                        pid = str(r['product_id'])
                        if pid in self.ids:
                            idx = self.ids.index(pid)
                            rows.append(self.vectors[idx])
                            weights.append(float(r['rating']))
                    if rows:
                        rows = np.array(rows)
                        weights = np.array(weights).reshape(-1, 1)
                        user_vec = (rows * weights).sum(axis=0) / weights.sum()

            if user_vec is None and user_history:
                idxs = [self.ids.index(pid) for pid in user_history if pid in self.ids]
                if idxs:
                    user_vec = self.vectors[idxs].mean(axis=0)

            if user_vec is None:
                # fallback: recommend most popular by average rating if available
                if not self.ratings_df.empty:
                    avg = self.ratings_df.groupby('product_id')['rating'].mean()
                    sorted_ids = [str(pid) for pid in avg.sort_values(ascending=False).index if str(pid) in self.ids]
                    return sorted_ids[:n]
                return self.ids[:n]

            user_vec = user_vec.reshape(1, -1)
            sims = cosine_similarity(user_vec, self.vectors).flatten()
            # exclude seen
            if user_history:
                for pid in user_history:
                    if pid in self.ids:
                        sims[self.ids.index(pid)] = -1

            top = sims.argsort()[::-1][:n]
            return [self.ids[i] for i in top]

        # Else if we have a ratings-based similarity matrix -> item-based CF
        if getattr(self, 'similarity_matrix', None) is not None and not self.ratings_df.empty:
            # build user's rating vector across sim_items
            if user_id is not None:
                user_r = self.ratings_df[self.ratings_df['user_id'] == user_id]
                if user_r.empty:
                    # fallback to history if provided
                    user_ratings = {}
                    if user_history:
                        for pid in user_history:
                            user_ratings[pid] = 1.0
                else:
                    user_ratings = {str(r['product_id']): float(r['rating']) for _, r in user_r.iterrows()}
            elif user_history:
                user_ratings = {pid: 1.0 for pid in user_history}
            else:
                # popularity fallback
                if not self.ratings_df.empty:
                    avg = self.ratings_df.groupby('product_id')['rating'].mean()
                    sorted_ids = [str(pid) for pid in avg.sort_values(ascending=False).index if str(pid) in self.ids]
                    return sorted_ids[:n]
                return self.ids[:n]

            # create rating vector aligned with sim_items
            item_index = {pid: i for i, pid in enumerate(self.sim_items)}
            user_vec = np.zeros(len(self.sim_items))
            for pid, val in user_ratings.items():
                if pid in item_index:
                    user_vec[item_index[pid]] = val

            # score items by similarity-weighted sum
            scores = self.similarity_matrix.dot(user_vec)
            # zero out items the user already rated
            for pid in user_ratings.keys():
                if pid in item_index:
                    scores[item_index[pid]] = -1

            top_idx = np.argsort(scores)[::-1][:n]
            return [self.sim_items[i] for i in top_idx if scores[i] > -1][:n]

        # No data -> empty
        return []
