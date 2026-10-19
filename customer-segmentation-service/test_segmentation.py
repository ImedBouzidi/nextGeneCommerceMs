import requests
import time

URL = 'http://127.0.0.1:5010/run-segmentation'

if __name__ == '__main__':
    print('Waiting briefly for service...')
    time.sleep(1)
    print('Run segmentation without sending events:')
    r = requests.post(URL, json={'n_clusters': 3, 'send': False}, timeout=10)
    print(r.status_code)
    print(r.json())
