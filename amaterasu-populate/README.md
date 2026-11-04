# amaterasu-populate

Seeds a running API with a sample Docker CTF room and its challenges, owned by the admin account.

```bash
uv venv && uv pip install --require-hashes -r requirements.txt
```

```bash
AMATERASU_PASSWORD=<admin password> uv run --no-project python populate.py
```

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `AMATERASU_URL` | `http://127.0.0.1:8080/amaterasu-rest/api` | API base URL |
| `AMATERASU_USERNAME` | `amaterasu_admin` | Admin account to log in as |
| `AMATERASU_PASSWORD` | | Its password |

The challenges are defined in `docker_challenge.py` and the room in `room.py`.
