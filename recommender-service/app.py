from flask import Flask, request, jsonify
import os
import pathlib
import yaml
from model import Recommender
import threading
import time

app = Flask(__name__)
# Allow passing DATABASE_URL env var, and optional local products file
# Load local config file if present (no need to export env vars each time)
cfg_path = pathlib.Path(__file__).parent.joinpath('config.yaml')
config = {}
if cfg_path.exists():
    try:
        with open(cfg_path, 'r') as f:
            config = yaml.safe_load(f) or {}
    except Exception:
        config = {}

# precedence: explicit env vars > config.yaml > defaults
db_url = os.environ.get('DATABASE_URL') or config.get('database_url')
products_path = os.environ.get('PRODUCTS_PATH') or config.get('products_path', 'recommender-service/data/products.json')
catalogue_url = os.environ.get('CATALOGUE_URL') or config.get('catalogue_url')
rec = Recommender(products_path=products_path, db_url=db_url, catalogue_url=catalogue_url)


def _background_refresher(recommender, interval_sec=30):
    """Daemon thread that periodically refreshes catalogue data if CATALOGUE_URL is set."""
    while True:
        try:
            if recommender.catalogue_url:
                recommender._load_from_catalogue(recommender.catalogue_url)
        except Exception:
            # ignore transient errors
            pass
        time.sleep(interval_sec)


if catalogue_url or db_url:
    try:
        interval = int(os.environ.get('REFRESH_INTERVAL_SEC') or config.get('refresh_interval_sec') or 30)
    except Exception:
        interval = 30
    t = threading.Thread(target=_background_refresher, args=(rec, interval), daemon=True)
    t.start()

@app.route('/health')
def health():
    return jsonify({"status": "ok"})

@app.route('/recommend', methods=['POST'])
def recommend():
    payload = request.get_json(force=True)
    user_history = payload.get('history', [])
    n = int(payload.get('n', 5))
    recommendations = rec.recommend(user_history, n)
    return jsonify({"recommendations": recommendations})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
