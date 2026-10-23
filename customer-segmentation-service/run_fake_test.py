import requests


if __name__ == "__main__":
    response = requests.post(
        "http://127.0.0.1:5010/run-segmentation",
        json={"n_clusters": 3, "send": False},
        timeout=10,
    )
    response.raise_for_status()
    print(response.json())
