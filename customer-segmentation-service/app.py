from flask import Flask, request, jsonify
import os
from segmentation import load_orders, compute_rfm, run_kmeans, assign_discounts, send_promotions
import pathlib

app = Flask(__name__)
BASE = pathlib.Path(__file__).parent

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

if __name__ == '__main__':
    if __name__ == '__main__':
        port = int(os.environ.get('PORT', 5010))
        app.run(host='0.0.0.0', port=port, debug=True)
