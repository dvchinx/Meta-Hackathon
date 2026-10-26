import os

# Tests never call the real Meta API, whatever ../.env says. Env vars win over the env file.
os.environ["META_API_ENABLED"] = "false"
os.environ["META_API_KEY"] = ""
