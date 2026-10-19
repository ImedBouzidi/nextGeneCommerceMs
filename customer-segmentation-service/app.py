from flask import Flask, request, jsonify
import os
import pandas as pd
from segmentation import (
    load_orders, compute_rfm, run_kmeans, assign_discounts, send_promotions,
    generate_synthetic_data, save_synthetic_data, load_ratings, load_interactions,
    compute_customer_metrics
)
import pathlib

app = Flask(__name__)
BASE = pathlib.Path(__file__).parent
DATA_DIR = BASE.joinpath('data')

@app.route('/health')
def health():
    return jsonify({'status': 'ok'})

@app.route('/run-segmentation', methods=['POST'])
def run_segmentation():
    data = request.get_json(force=True) if request.data else {}
    orders_path = data.get('orders_path') or str(BASE.joinpath('data/mock_orders.csv'))
    n_clusters = int(data.get('n_clusters', 3))
    send = bool(data.get('send', False))

    orders = load_orders(orders_path)
    rfm = compute_rfm(orders)
    seg, _, _ = run_kmeans(rfm, n_clusters=n_clusters)
    promos = assign_discounts(seg)

    if send:
        kafka_bootstrap = data.get('kafka_bootstrap') or os.environ.get('KAFKA_BOOTSTRAP')
        send_promotions(promos, kafka_bootstrap=kafka_bootstrap)

    return jsonify({'promotions': promos.to_dict(orient='records')})

@app.route('/generate-data', methods=['POST'])
def generate_data():
    """Generate synthetic customers, orders, ratings, and interactions."""
    data = request.get_json(force=True) if request.data else {}
    n_customers = int(data.get('n_customers', 50))
    max_orders = int(data.get('max_orders_per_customer', 15))
    days_span = int(data.get('days_span', 365))
    products_count = int(data.get('products_count', 20))
    
    orders_df, ratings_df, interactions_df = generate_synthetic_data(
        n_customers=n_customers,
        max_orders_per_customer=max_orders,
        days_span=days_span,
        products_count=products_count
    )
    
    save_synthetic_data(orders_df, ratings_df, interactions_df, DATA_DIR)
    
    return jsonify({
        'message': 'Synthetic data generated successfully',
        'orders_count': len(orders_df),
        'ratings_count': len(ratings_df),
        'interactions_count': len(interactions_df),
        'customers_count': n_customers
    })

@app.route('/top-customers', methods=['GET'])
def get_top_customers():
    """Get top customers by buying, ratings, and interactions."""
    # Load data
    orders_path = DATA_DIR / 'orders.csv'
    ratings_path = DATA_DIR / 'ratings.csv'
    interactions_path = DATA_DIR / 'interactions.csv'
    
    # Check if synthetic data exists, otherwise use mock data
    if not orders_path.exists():
        orders_path = BASE.joinpath('data/mock_orders.csv')
        # Create empty ratings/interactions for mock data
        ratings_df = pd.DataFrame(columns=['rating_id', 'customer_id', 'product_id', 'order_id', 'rating', 'rating_date'])
        interactions_df = pd.DataFrame(columns=['interaction_id', 'customer_id', 'product_id', 'interaction_type', 'interaction_date', 'duration_seconds'])
    else:
        ratings_df = load_ratings(str(ratings_path))
        interactions_df = load_interactions(str(interactions_path))
    
    orders_df = load_orders(str(orders_path))
    
    # Compute metrics
    metrics = compute_customer_metrics(orders_df, ratings_df, interactions_df)
    
    # Get query parameters
    limit = int(request.args.get('limit', 10))
    sort_by = request.args.get('sort_by', 'engagement_score')  # engagement_score, buying_score, rating_score, interaction_score, frequency, monetary
    
    # Validate sort_by
    valid_sorts = ['engagement_score', 'buying_score', 'rating_score', 'interaction_score', 'frequency', 'monetary', 'recency', 'avg_rating', 'total_ratings', 'total_interactions']
    if sort_by not in valid_sorts:
        sort_by = 'engagement_score'
    
    # Sort and limit
    top_customers = metrics.sort_values(sort_by, ascending=False).head(limit)
    
    # Format response
    result = top_customers.to_dict(orient='records')
    
    return jsonify({
        'customers': result,
        'total_customers': len(metrics),
        'sort_by': sort_by,
        'limit': limit
    })

@app.route('/customer/<customer_id>', methods=['GET'])
def get_customer_detail(customer_id):
    """Get detailed metrics for a specific customer."""
    orders_path = DATA_DIR / 'orders.csv'
    ratings_path = DATA_DIR / 'ratings.csv'
    interactions_path = DATA_DIR / 'interactions.csv'
    
    if not orders_path.exists():
        orders_path = BASE.joinpath('data/mock_orders.csv')
        ratings_df = pd.DataFrame(columns=['rating_id', 'customer_id', 'product_id', 'order_id', 'rating', 'rating_date'])
        interactions_df = pd.DataFrame(columns=['interaction_id', 'customer_id', 'product_id', 'interaction_type', 'interaction_date', 'duration_seconds'])
    else:
        ratings_df = load_ratings(str(ratings_path))
        interactions_df = load_interactions(str(interactions_path))
    
    orders_df = load_orders(str(orders_path))
    
    # Filter for this customer
    customer_orders = orders_df[orders_df['customer_id'] == customer_id]
    customer_ratings = ratings_df[ratings_df['customer_id'] == customer_id] if not ratings_df.empty else pd.DataFrame()
    customer_interactions = interactions_df[interactions_df['customer_id'] == customer_id] if not interactions_df.empty else pd.DataFrame()
    
    if customer_orders.empty:
        return jsonify({'error': 'Customer not found'}), 404
    
    # Compute metrics
    metrics = compute_customer_metrics(orders_df, ratings_df, interactions_df)
    customer_metrics = metrics[metrics['customer_id'] == customer_id]
    
    if customer_metrics.empty:
        return jsonify({'error': 'Customer not found'}), 404
    
    return jsonify({
        'customer': customer_metrics.to_dict(orient='records')[0],
        'orders': customer_orders.to_dict(orient='records'),
        'ratings': customer_ratings.to_dict(orient='records'),
        'interactions': customer_interactions.to_dict(orient='records')
    })

if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5010))
    app.run(host='0.0.0.0', port=port, debug=True)
