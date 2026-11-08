def get_room(admin_user):
    return {
        "name": "Docker Room",
        "surroundTag": "docker{?}",
        "scenario": (
            "## Situation\n"
            "A set of Docker practice questions.\n\n"
            "## Objectives\n"
            "- Answer each question below.\n"
        ),
    }
