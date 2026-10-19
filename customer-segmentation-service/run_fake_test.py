import random
import datetime
from pathlib import Path
import pandas as pd

from segmentation import load_orders, compute_rfm, run_kmeans, assign_discounts


def generate_fake_orders(path, n_customers=25, max_orders_per_customer=8, days_span=365):
    rows = []
    order_id = 1
    today = datetime.date.today()
    for i in range(n_customers):
        cust = f"cust{i+1}"
        n_orders = random.randint(1, max_orders_per_customer)
        for _ in range(n_orders):
            days_ago = random.randint(0, days_span)
            order_date = today - datetime.timedelta(days=days_ago)
            amount = round(random.uniform(5.0, 1000.0), 2)
            rows.append({
                'order_id': order_id,
                'customer_id': cust,
                'order_date': order_date.isoformat(),
                'total_amount': amount
            })
            order_id += 1

    df = pd.DataFrame(rows)
    path.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(path, index=False)
    return df


if __name__ == '__main__':
    base = Path(__file__).parent
    data_path = base.joinpath('data', 'fake_orders.csv')

    print('Generating fake orders:', data_path)
    generate_fake_orders(data_path, n_customers=30, max_orders_per_customer=10, days_span=400)

    print('Loading orders and running segmentation...')
    orders = load_orders(str(data_path))
    rfm = compute_rfm(orders)
    seg_df, kmeans, scaler = run_kmeans(rfm, n_clusters=4)
    promos = assign_discounts(seg_df)

    print('\nPromotions sample (first 10 records):')
    print(promos.head(10).to_string(index=False))
