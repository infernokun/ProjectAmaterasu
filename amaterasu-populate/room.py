def get_room( admin_user):
    return {
        "name": "Docker Room",
        "creator": admin_user,
        "surroundTag": "docker{?}"
    }