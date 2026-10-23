import pandas as pd
from sklearn.cluster import KMeans
from sklearn.preprocessing import StandardScaler
import json
import os
from kafka import KafkaProducer


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
