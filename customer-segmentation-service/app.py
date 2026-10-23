import os

import pandas as pd
from flask import Flask, jsonify, request

from database import load_database_data, seed_database
from segmentation import (
    assign_discounts,
    compute_customer_metrics,
    compute_rfm,
    run_kmeans,
    send_promotions,
)


app = Flask(__name__)


def records(frame):
    """Convert a DataFrame to JSON-safe records (NaN/NaT/inf become null)."""
    return (
        frame.astype(object)
        .where(pd.notnull(frame), None)
        .to_dict(orient="records")
    )


@app.route("/health")
def health():
    return jsonify({"status": "ok"})


@app.route("/run-segmentation", methods=["POST"])
def run_segmentation():
    data = request.get_json(force=True) if request.data else {}
    n_clusters = int(data.get("n_clusters", 3))
    orders, _, _ = load_database_data()
    if orders.empty:
        return jsonify({"error": "No customer orders found in the database"}), 503

    rfm = compute_rfm(orders)
    segmented, _, _ = run_kmeans(rfm, n_clusters=n_clusters)
    promotions = assign_discounts(segmented)

    if bool(data.get("send", False)):
        kafka_bootstrap = data.get("kafka_bootstrap") or os.environ.get("KAFKA_BOOTSTRAP")
        send_promotions(promotions, kafka_bootstrap=kafka_bootstrap)

    return jsonify({"promotions": records(promotions)})


@app.route("/generate-data", methods=["POST"])
def generate_data():
    data = request.get_json(force=True) if request.data else {}
    inserted = seed_database(int(data.get("n_customers", 60)))
    return jsonify(
        {
            "message": "Database clients and interaction histories seeded successfully",
            "inserted_clients": inserted,
        }
    )


@app.route("/top-customers", methods=["GET"])
def get_top_customers():
    orders, ratings, interactions = load_database_data()
    metrics = compute_customer_metrics(orders, ratings, interactions)
    limit = int(request.args.get("limit", 10))
    sort_by = request.args.get("sort_by", "engagement_score")
    valid_sorts = {
        "engagement_score",
        "buying_score",
        "rating_score",
        "interaction_score",
        "frequency",
        "monetary",
        "recency",
        "avg_rating",
        "total_ratings",
        "total_interactions",
    }
    if sort_by not in valid_sorts:
        sort_by = "engagement_score"

    result = metrics.sort_values(sort_by, ascending=False).head(limit)
    return jsonify(
        {
            "customers": records(result),
            "total_customers": len(metrics),
            "sort_by": sort_by,
            "limit": limit,
        }
    )


@app.route("/customer/<customer_id>", methods=["GET"])
def get_customer_detail(customer_id):
    orders, ratings, interactions = load_database_data()
    customer_orders = orders[orders["customer_id"] == customer_id]
    customer_ratings = ratings[ratings["customer_id"] == customer_id]
    customer_interactions = interactions[interactions["customer_id"] == customer_id]
    if customer_orders.empty:
        return jsonify({"error": "Customer not found"}), 404

    metrics = compute_customer_metrics(orders, ratings, interactions)
    customer_metrics = metrics[metrics["customer_id"] == customer_id]
    if customer_metrics.empty:
        return jsonify({"error": "Customer not found"}), 404

    return jsonify(
        {
            "customer": records(customer_metrics)[0],
            "orders": records(customer_orders),
            "ratings": records(customer_ratings),
            "interactions": records(customer_interactions),
        }
    )


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", 5010)), debug=True)
