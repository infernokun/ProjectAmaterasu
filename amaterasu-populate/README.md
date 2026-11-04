# amaterasu-populate

Seeds a running API with a sample Docker CTF room and its challenges, owned by the default admin.

```bash
uv venv && uv pip install --require-hashes -r requirements.txt
```

```bash
uv run --no-project python populate.py
```

The API URL is set at the top of `populate.py` (`http://127.0.0.1:8080/amaterasu-rest/api`). The challenges are defined in `docker_challenge.py` and the room in `room.py`.
