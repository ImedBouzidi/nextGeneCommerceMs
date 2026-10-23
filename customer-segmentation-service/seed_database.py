from database import seed_database


if __name__ == "__main__":
    inserted = seed_database(60)
    print(f"Inserted {inserted} new clients and their interaction histories.")
