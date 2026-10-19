# Recommender Service (minimal)

Simple Flask-based recommendations PoC.

Endpoints
- `GET /health` : health check
- `POST /recommend` : JSON `{ "history": ["p1","p2"], "n": 5 }`

Run locally

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python app.py
```

Next steps
- Replace static vectors with embeddings from product descriptions
- Add Kafka consumer/producer to receive user events and publish recommendations
- Add tests and dockerfile
 
Database integration

- To use ratings/products from a database set `DATABASE_URL` (Postgres, MySQL, SQLite uri supported by SQLAlchemy).
- Example (Postgres):

```bash
export DATABASE_URL=postgresql://user:pass@host:5432/dbname
python app.py
```

- The service expects a `products` table with columns `(id, vector)` where `vector` can be a JSON array or comma-separated numbers,
  and a `ratings` table with `(user_id, product_id, rating)` for collaborative signals.

- If `DATABASE_URL` is not provided, the service falls back to `recommender-service/data/products.json`.

Catalogue service integration

- To use the catalogue service as the source of products and reviews set `CATALOGUE_URL` to the base URL of the catalogue service (for example `http://catalogue-service:8080`). The recommender will call:
    - `GET {CATALOGUE_URL}/api/products` to list products
    - `GET {CATALOGUE_URL}/api/reviews/product/{productId}` to collect ratings

- Priority: `CATALOGUE_URL` is used first, then `DATABASE_URL`, then the local JSON file fallback.

API Gateway / CORS

- The API Gateway already contains a `CorsConfig` allowing requests from `http://localhost:4200`. Ensure the gateway routes client requests to the recommender service (e.g. an API path like `/recommender/**`).

Local startup without env vars

- You can create or edit `recommender-service/config.yaml` to provide defaults so you don't need to export `CATALOGUE_URL` or `DATABASE_URL` every time. Example `config.yaml`:

```yaml
catalogue_url: http://localhost:8081
database_url: sqlite:////app/recommender.db
products_path: recommender-service/data/products.json
refresh_interval_sec: 30
```

- `app.py` reads `config.yaml` automatically; environment variables still override these values if present.
