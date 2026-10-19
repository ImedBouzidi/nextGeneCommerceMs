import requests
import time

URL = 'http://127.0.0.1:5000/recommend'

def test_recommend(history=None, n=5):
    payload = {'history': history or [], 'n': n}
    r = requests.post(URL, json=payload, timeout=5)
    print('status:', r.status_code)
    print('response:', r.json())

if __name__ == '__main__':
    print('Give the recommender a few seconds to start if needed...')
    time.sleep(1)
    print('Test 1: no history')
    test_recommend()
    print('\nTest 2: history [p1]')
    test_recommend(history=['p1'])
    print('\nTest 3: history [p3, p4]')
    test_recommend(history=['p3','p4'], n=3)
