import pandas as pd
import numpy as np
from sklearn.cluster import KMeans
from sklearn.preprocessing import StandardScaler
from datetime import datetime, timedelta
import json
import os
import random
from pathlib import Path
from kafka import KafkaProducer


def load_orders(path):
    return pd.read_csv(path, parse_dates=['order_date'])


def generate_synthetic_data(n_customers=50, max_orders_per_customer=15, days_span=365, products_count=20):
    """Generate synthetic customers, orders, ratings, and interactions."""
    rows_orders = []
    rows_ratings = []
    rows_interactions = []
    order_id = 1
    rating_id = 1
    interaction_id = 1
    today = datetime.now().date()
    
    # Product catalog
    products = [f"prod_{i+1}" for i in range(products_count)]
    product_prices = {p: round(random.uniform(10.0, 500.0), 2) for p in products}
    product_categories = {p: random.choice(['Electronics', 'Clothing', 'Home', 'Sports', 'Books']) for p in products}
    
    for i in range(n_customers):
        cust = f"cust_{i+1:03d}"
        n_orders = random.randint(1, max_orders_per_customer)
        
        for _ in range(n_orders):
            days_ago = random.randint(0, days_span)
            order_date = today - timedelta(days=days_ago)
            product = random.choice(products)
            qty = random.randint(1, 3)
            amount = round(product_prices[product] * qty * random.uniform(0.8, 1.2), 2)
            
            rows_orders.append({
                'order_id': order_id,
                'customer_id': cust,
                'order_date': order_date.isoformat(),
                'total_amount': amount,
                'product_id': product,
                'quantity': qty,
                'category': product_categories[product]
            })
            order_id += 1
            
            # Generate rating for this order (70% chance)
            if random.random() < 0.7:
                rating = round(random.uniform(1.0, 5.0), 1)
                rows_ratings.append({
                    'rating_id': rating_id,
                    'customer_id': cust,
                    'product_id': product,
                    'order_id': order_id - 1,
                    'rating': rating,
                    'rating_date': (order_date + timedelta(days=random.randint(1, 7))).isoformat()
                })
                rating_id += 1
        
        # Generate interactions (views, clicks, cart adds) independent of orders
        n_interactions = random.randint(5, 50)
        for _ in range(n_interactions):
            days_ago = random.randint(0, days_span)
            interaction_date = today - timedelta(days=days_ago)
            interaction_type = random.choices(
                ['view', 'click', 'cart_add', 'wishlist', 'search'],
                weights=[0.4, 0.25, 0.15, 0.1, 0.1]
            )[0]
            product = random.choice(products)
            rows_interactions.append({
                'interaction_id': interaction_id,
                'customer_id': cust,
                'product_id': product,
                'interaction_type': interaction_type,
                'interaction_date': interaction_date.isoformat(),
                'duration_seconds': random.randint(5, 300) if interaction_type == 'view' else None
            })
            interaction_id += 1
    
    return (
        pd.DataFrame(rows_orders),
        pd.DataFrame(rows_ratings),
        pd.DataFrame(rows_interactions)
    )


def save_synthetic_data(orders_df, ratings_df, interactions_df, base_path):
    """Save synthetic data to CSV files."""
    base = Path(base_path)
    base.mkdir(parents=True, exist_ok=True)
    orders_df.to_csv(base / 'orders.csv', index=False)
    ratings_df.to_csv(base / 'ratings.csv', index=False)
    interactions_df.to_csv(base / 'interactions.csv', index=False)


def load_ratings(path):
    return pd.read_csv(path, parse_dates=['rating_date'])


def load_interactions(path):
    return pd.read_csv(path, parse_dates=['interaction_date'])


def compute_rfm(orders, as_of=None):
    # Ensure order_date is datetime
    orders = orders.copy()
    if not pd.api.types.is_datetime64_any_dtype(orders['order_date']):
        orders['order_date'] = pd.to_datetime(orders['order_date'])
    if as_of is None:
        as_of = orders['order_date'].max() + pd.Timedelta(days=1)
    grouped = orders.groupby('customer_id').agg({
        'order_date': lambda x: (as_of - x.max()).days,
        'order_id': 'count',
        'total_amount': 'sum'
    }).rename(columns={'order_date': 'recency', 'order_id': 'frequency', 'total_amount': 'monetary'})
    return grouped.reset_index()


def compute_customer_metrics(orders, ratings, interactions):
    """Compute comprehensive customer metrics for ranking."""
    # RFM from orders
    rfm = compute_rfm(orders)
    
    # Ratings metrics
    if not ratings.empty:
        rating_metrics = ratings.groupby('customer_id').agg(
            avg_rating=('rating', 'mean'),
            total_ratings=('rating', 'count'),
            max_rating=('rating', 'max')
        ).reset_index()
    else:
        rating_metrics = pd.DataFrame(columns=['customer_id', 'avg_rating', 'total_ratings', 'max_rating'])
    
    # Interaction metrics
    if not interactions.empty:
        interaction_metrics = interactions.groupby('customer_id').agg(
            total_interactions=('interaction_id', 'count'),
            total_views=('interaction_type', lambda x: (x == 'view').sum()),
            total_clicks=('interaction_type', lambda x: (x == 'click').sum()),
            total_cart_adds=('interaction_type', lambda x: (x == 'cart_add').sum()),
            total_wishlist=('interaction_type', lambda x: (x == 'wishlist').sum()),
            avg_session_duration=('duration_seconds', 'mean')
        ).reset_index()
    else:
        interaction_metrics = pd.DataFrame(columns=['customer_id', 'total_interactions', 'total_views', 
                                                      'total_clicks', 'total_cart_adds', 'total_wishlist', 
                                                      'avg_session_duration'])
    
    # Merge all metrics
    metrics = rfm.merge(rating_metrics, on='customer_id', how='left')
    metrics = metrics.merge(interaction_metrics, on='customer_id', how='left')
    
    # Fill NaN values
    metrics = metrics.fillna({
        'avg_rating': 0, 'total_ratings': 0, 'max_rating': 0,
        'total_interactions': 0, 'total_views': 0, 'total_clicks': 0,
        'total_cart_adds': 0, 'total_wishlist': 0, 'avg_session_duration': 0
    })
    
    # Compute composite scores
    metrics['buying_score'] = (
        (metrics['frequency'] / metrics['frequency'].max() if metrics['frequency'].max() > 0 else 0) * 0.4 +
        (metrics['monetary'] / metrics['monetary'].max() if metrics['monetary'].max() > 0 else 0) * 0.4 +
        (1 - metrics['recency'] / metrics['recency'].max() if metrics['recency'].max() > 0 else 0) * 0.2
    )
    
    metrics['rating_score'] = metrics['avg_rating'] / 5.0 if metrics['avg_rating'].max() > 0 else 0
    metrics['interaction_score'] = (
        metrics['total_interactions'] / metrics['total_interactions'].max() if metrics['total_interactions'].max() > 0 else 0
    )
    
    # Overall engagement score
    metrics['engagement_score'] = (
        metrics['buying_score'] * 0.5 +
        metrics['rating_score'] * 0.2 +
        metrics['interaction_score'] * 0.3
    )
    
    return metrics


def run_kmeans(rfm_df, n_clusters=3, random_state=42):
    features = rfm_df[['recency', 'frequency', 'monetary']].copy()
    scaler = StandardScaler()
    X = scaler.fit_transform(features)
    kmeans = KMeans(n_clusters=n_clusters, random_state=random_state)
    labels = kmeans.fit_predict(X)
    rfm_df['segment'] = labels.astype(str)
    return rfm_df, kmeans, scaler


def assign_discounts(segmented_df):
    df = segmented_df.copy()
    df['recency_inv'] = (df['recency'].max() - df['recency'])
    freq_range = (df['frequency'].max() - df['frequency'].min())
    mon_range = (df['monetary'].max() - df['monetary'].min())
    df['freq_n'] = (df['frequency'] - df['frequency'].min()) / (freq_range if freq_range else 1)
    df['mon_n'] = (df['monetary'] - df['monetary'].min()) / (mon_range if mon_range else 1)
    df['loyalty_score'] = df['recency_inv'] + df['freq_n'] + df['mon_n']

    seg_scores = df.groupby('segment')['loyalty_score'].mean().sort_values(ascending=False)
    discounts = {}
    seg_order = list(seg_scores.index)
    for i, seg in enumerate(seg_order):
        if i == 0:
            discounts[seg] = 20
        elif i == 1:
            discounts[seg] = 10
        else:
            discounts[seg] = 5

    df['discount_pct'] = df['segment'].map(discounts)
    return df[['customer_id', 'segment', 'discount_pct', 'loyalty_score']]


def send_promotions(promotions, kafka_bootstrap=None, topic='promotions'):
    kafka_bootstrap = kafka_bootstrap or os.environ.get('KAFKA_BOOTSTRAP', 'localhost:9092')
    producer = KafkaProducer(bootstrap_servers=kafka_bootstrap,
                             value_serializer=lambda v: json.dumps(v).encode('utf-8'))
    for _, row in promotions.iterrows():
        payload = {
            'customer_id': row['customer_id'],
            'segment': str(row['segment']),
            'discount_pct': int(row['discount_pct']),
            'message': f"Exclusive {int(row['discount_pct'])}% off for valued customers!"
        }
        producer.send(topic, value=payload)
    producer.flush()
    producer.close()


if __name__ == '__main__':
    orders = load_orders('data/mock_orders.csv')
    rfm = compute_rfm(orders)
    seg, k, s = run_kmeans(rfm, n_clusters=3)
    promos = assign_discounts(seg)
    print(promos.to_json(orient='records', force_ascii=False))
