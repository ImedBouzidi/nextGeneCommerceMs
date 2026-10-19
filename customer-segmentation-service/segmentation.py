import pandas as pd
import numpy as np
from sklearn.cluster import KMeans
from sklearn.preprocessing import StandardScaler
from datetime import datetime
import json
import os
from kafka import KafkaProducer


def load_orders(path):
    return pd.read_csv(path, parse_dates=['order_date'])


def compute_rfm(orders, as_of=None):
    if as_of is None:
        as_of = orders['order_date'].max() + pd.Timedelta(days=1)
    grouped = orders.groupby('customer_id').agg({
        'order_date': lambda x: (as_of - x.max()).days,
        'order_id': 'count',
        'total_amount': 'sum'
    }).rename(columns={'order_date': 'recency', 'order_id': 'frequency', 'total_amount': 'monetary'})
    return grouped.reset_index()


def run_kmeans(rfm_df, n_clusters=3, random_state=42):
    features = rfm_df[['recency', 'frequency', 'monetary']].copy()
    scaler = StandardScaler()
    X = scaler.fit_transform(features)
    kmeans = KMeans(n_clusters=n_clusters, random_state=random_state)
    labels = kmeans.fit_predict(X)
    rfm_df['segment'] = labels.astype(str)
    return rfm_df, kmeans, scaler


def assign_discounts(segmented_df):
    # Simple heuristic: lower recency and higher frequency/monetary -> better tier
    # Compute loyalty score: inverse recency + normalized frequency + normalized monetary
    df = segmented_df.copy()
    df['recency_inv'] = (df['recency'].max() - df['recency'])
    freq_range = (df['frequency'].max() - df['frequency'].min())
    mon_range = (df['monetary'].max() - df['monetary'].min())
    df['freq_n'] = (df['frequency'] - df['frequency'].min()) / (freq_range if freq_range else 1)
    df['mon_n'] = (df['monetary'] - df['monetary'].min()) / (mon_range if mon_range else 1)
    df['loyalty_score'] = df['recency_inv'] + df['freq_n'] + df['mon_n']

    # map segment to discount based on average loyalty_score per segment
    seg_scores = df.groupby('segment')['loyalty_score'].mean().sort_values(ascending=False)
    discounts = {}
    # top segment -> 20%, next -> 10%, others -> 5%
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
    # quick local run
    orders = load_orders('data/mock_orders.csv')
    rfm = compute_rfm(orders)
    seg, k, s = run_kmeans(rfm, n_clusters=3)
    promos = assign_discounts(seg)
    print(promos.to_json(orient='records', force_ascii=False))
