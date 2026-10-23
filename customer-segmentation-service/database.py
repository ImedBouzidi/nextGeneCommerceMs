import os
import random
from datetime import date, timedelta
from typing import Any

import pandas as pd
import psycopg2
from psycopg2.extras import RealDictCursor


ORDER_COLUMNS = [
    "order_id",
    "customer_id",
    "order_date",
    "total_amount",
    "product_id",
    "quantity",
    "category",
]
RATING_COLUMNS = [
    "rating_id",
    "customer_id",
    "product_id",
    "order_id",
    "rating",
    "rating_date",
]
INTERACTION_COLUMNS = [
    "interaction_id",
    "customer_id",
    "product_id",
    "interaction_type",
    "interaction_date",
    "duration_seconds",
]


def database_connection() -> psycopg2.extensions.connection:
    database_url = os.environ.get("CUSTOMER_DATABASE_URL")
    if database_url:
        return psycopg2.connect(database_url)
    return psycopg2.connect(
        host=os.environ.get("DB_HOST", "localhost"),
        port=int(os.environ.get("DB_PORT", "5432")),
        dbname=os.environ.get("DB_NAME", "buyme_users"),
        user=os.environ.get("DB_USERNAME", "imed"),
        password=os.environ.get("AUTH_DB_PASSWORD", "admin"),
    )


def ensure_behavior_tables(connection: psycopg2.extensions.connection) -> None:
    with connection.cursor() as cursor:
        cursor.execute(
            """
            CREATE TABLE IF NOT EXISTS customer_orders (
                id BIGSERIAL PRIMARY KEY,
                customer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                order_date DATE NOT NULL,
                total_amount NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
                product_id VARCHAR(100) NOT NULL,
                quantity INTEGER NOT NULL CHECK (quantity > 0),
                category VARCHAR(100) NOT NULL
            );
            CREATE TABLE IF NOT EXISTS customer_ratings (
                id BIGSERIAL PRIMARY KEY,
                customer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                product_id VARCHAR(100) NOT NULL,
                order_id BIGINT REFERENCES customer_orders(id) ON DELETE SET NULL,
                rating NUMERIC(2, 1) NOT NULL CHECK (rating BETWEEN 1 AND 5),
                rating_date DATE NOT NULL
            );
            CREATE TABLE IF NOT EXISTS customer_interactions (
                id BIGSERIAL PRIMARY KEY,
                customer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                product_id VARCHAR(100) NOT NULL,
                interaction_type VARCHAR(30) NOT NULL,
                interaction_date DATE NOT NULL,
                duration_seconds INTEGER
            );
            """
        )
    connection.commit()


def _rows(connection: psycopg2.extensions.connection, query: str) -> list[dict[str, Any]]:
    with connection.cursor(cursor_factory=RealDictCursor) as cursor:
        cursor.execute(query)
        return [dict(row) for row in cursor.fetchall()]


def load_database_data() -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    with database_connection() as connection:
        ensure_behavior_tables(connection)
        orders = _rows(
            connection,
            """
            SELECT id AS order_id, customer_id::text, order_date,
                   total_amount::double precision, 
                   product_id, quantity, category
            FROM customer_orders
            ORDER BY id
            """,
        )
        ratings = _rows(
            connection,
            """
            SELECT id AS rating_id, customer_id::text, product_id, order_id,
                   rating::double precision, rating_date
            FROM customer_ratings
            ORDER BY id
            """,
        )
        interactions = _rows(
            connection,
            """
            SELECT id AS interaction_id, customer_id::text, product_id,
                   interaction_type, interaction_date, duration_seconds
            FROM customer_interactions
            ORDER BY id
            """,
        )

    return (
        pd.DataFrame(orders, columns=ORDER_COLUMNS),
        pd.DataFrame(ratings, columns=RATING_COLUMNS),
        pd.DataFrame(interactions, columns=INTERACTION_COLUMNS),
    )


def seed_clients(
    connection: psycopg2.extensions.connection,
    client_count: int = 60,
    seed: int = 20261023,
) -> int:
    ensure_behavior_tables(connection)
    generator = random.Random(seed)
    categories = ["Electronics", "Clothing", "Home", "Sports", "Books"]
    first_names = ["Amine", "Sonia", "Yassine", "Ines", "Meriem", "Nour", "Hatem", "Rania"]
    last_names = ["BenAli", "Trabelsi", "Mansouri", "Gharbi", "Jlassi", "Kefi", "Ayari"]
    today = date.today()
    inserted = 0

    with connection.cursor() as cursor:
        for index in range(1, client_count + 1):
            email = f"segmentation.client{index:03d}@example.com"
            cursor.execute("SELECT id FROM users WHERE email = %s", (email,))
            row = cursor.fetchone()
            if row:
                customer_id = row[0]
            else:
                cursor.execute(
                    """
                    INSERT INTO users (
                        user_type, email, first_name, last_name, country, city, zip_code
                    ) VALUES ('CLIENT', %s, %s, %s, 'Tunisia', %s, %s)
                    RETURNING id
                    """,
                    (
                        email,
                        first_names[(index - 1) % len(first_names)],
                        last_names[(index - 1) % len(last_names)],
                        ["Tunis", "Sousse", "Sfax", "Bizerte"][(index - 1) % 4],
                        1000 + index,
                    ),
                )
                customer_id = cursor.fetchone()[0]
                cursor.execute(
                    "INSERT INTO users_roles (user_id, role_id) "
                    "SELECT %s, id FROM role WHERE name = 'CLIENT' "
                    "AND NOT EXISTS (SELECT 1 FROM users_roles WHERE user_id = %s AND role_id = id)",
                    (customer_id, customer_id),
                )
                inserted += 1

            cursor.execute(
                "SELECT 1 FROM customer_orders WHERE customer_id = %s LIMIT 1",
                (customer_id,),
            )
            if cursor.fetchone():
                continue

            product_count = 3 + (index % 8)
            order_ids: list[int] = []
            for order_index in range(product_count):
                days_ago = generator.randint(1, 365)
                product_id = f"prod_{generator.randint(1, 20):02d}"
                quantity = generator.randint(1, 3)
                amount = round(generator.uniform(25, 450) * quantity, 2)
                cursor.execute(
                    """
                    INSERT INTO customer_orders
                        (customer_id, order_date, total_amount, product_id, quantity, category)
                    VALUES (%s, %s, %s, %s, %s, %s)
                    RETURNING id
                    """,
                    (
                        customer_id,
                        today - timedelta(days=days_ago),
                        amount,
                        product_id,
                        quantity,
                        categories[generator.randrange(len(categories))],
                    ),
                )
                order_id = cursor.fetchone()[0]
                order_ids.append(order_id)
                if generator.random() < 0.7:
                    cursor.execute(
                        """
                        INSERT INTO customer_ratings
                            (customer_id, product_id, order_id, rating, rating_date)
                        VALUES (%s, %s, %s, %s, %s)
                        """,
                        (
                            customer_id,
                            product_id,
                            order_id,
                            round(generator.uniform(1, 5), 1),
                            today - timedelta(days=max(days_ago - 3, 0)),
                        ),
                    )

            for _ in range(8 + index % 28):
                interaction_type = generator.choices(
                    ["view", "click", "cart_add", "wishlist", "search"],
                    weights=[40, 25, 15, 10, 10],
                )[0]
                cursor.execute(
                    """
                    INSERT INTO customer_interactions
                        (customer_id, product_id, interaction_type, interaction_date, duration_seconds)
                    VALUES (%s, %s, %s, %s, %s)
                    """,
                    (
                        customer_id,
                        f"prod_{generator.randint(1, 20):02d}",
                        interaction_type,
                        today - timedelta(days=generator.randint(0, 365)),
                        generator.randint(5, 300) if interaction_type == "view" else None,
                    ),
                )

    connection.commit()
    return inserted


def seed_database(client_count: int = 60) -> int:
    with database_connection() as connection:
        return seed_clients(connection, client_count=client_count)
