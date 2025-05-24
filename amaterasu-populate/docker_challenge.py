from datetime import datetime, timedelta

def get_challenges(docker_room, admin_user):
    return [
        # Challenge 3 - Easy
        {
            "question": "What command is used to run a Docker container?",
            "maxAttempts": 3,
            "room": docker_room,
            "description": "This challenge tests your knowledge of running Docker containers.",
            "hints": ["Think about starting a container.", "The command begins with 'docker'."],
            "flags": [
                {
                    "flag": "docker run",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Commands",
            "difficultyLevel": "Easy",
            "points": 10,
            "author": admin_user['username'],
            "tags": ["docker", "commands", "containers"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'docker run' command creates and starts a new container from an image.",
            "relatedChallengeIds": []
        },
        
        # Challenge 4 - Easy
        {
            "question": "What command shows all Docker images on your system?",
            "maxAttempts": 3,
            "room": docker_room,
            "description": "This challenge tests your knowledge of listing Docker images.",
            "hints": ["Similar to listing containers but for images.", "Check the Docker images documentation."],
            "flags": [
                {
                    "flag": "docker images",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Commands",
            "difficultyLevel": "Easy",
            "points": 10,
            "author": admin_user['username'],
            "tags": ["docker", "images", "list"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'docker images' command lists all Docker images stored locally.",
            "relatedChallengeIds": []
        },
        
        # Challenge 5 - Easy
        {
            "question": "What command is used to stop a running Docker container?",
            "maxAttempts": 3,
            "room": docker_room,
            "description": "This challenge tests your knowledge of stopping Docker containers.",
            "hints": ["Think about halting a container.", "You'll need the container ID or name."],
            "flags": [
                {
                    "flag": "docker stop",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Commands",
            "difficultyLevel": "Easy",
            "points": 10,
            "author": admin_user['username'],
            "tags": ["docker", "containers", "stop"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'docker stop' command gracefully stops a running container.",
            "relatedChallengeIds": []
        },
        
        # Challenge 6 - Easy
        {
            "question": "What command removes a Docker container?",
            "maxAttempts": 3,
            "room": docker_room,
            "description": "This challenge tests your knowledge of removing Docker containers.",
            "hints": ["Think about deleting a container.", "The container must be stopped first."],
            "flags": [
                {
                    "flag": "docker rm",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Commands",
            "difficultyLevel": "Easy",
            "points": 10,
            "author": admin_user['username'],
            "tags": ["docker", "containers", "remove"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'docker rm' command removes one or more containers.",
            "relatedChallengeIds": []
        },
        
        # Challenge 7 - Easy
        {
            "question": "What command removes a Docker image?",
            "maxAttempts": 3,
            "room": docker_room,
            "description": "This challenge tests your knowledge of removing Docker images.",
            "hints": ["Similar to removing containers but for images.", "Make sure no containers are using the image."],
            "flags": [
                {
                    "flag": "docker rmi",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Commands",
            "difficultyLevel": "Easy",
            "points": 10,
            "author": admin_user['username'],
            "tags": ["docker", "images", "remove"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'docker rmi' command removes one or more images from the local system.",
            "relatedChallengeIds": []
        },
        
        # Challenge 8 - Medium
        {
            "question": "What Docker run flag allows you to run a container in detached mode?",
            "maxAttempts": 2,
            "room": docker_room,
            "description": "This challenge tests your knowledge of Docker run flags.",
            "hints": ["Detached mode runs the container in the background.", "It's a single letter flag."],
            "flags": [
                {
                    "flag": "-d",
                    "surroundWithTag": False,
                    "caseSensitive": True,
                    "weight": 1
                }
            ],
            "category": "Docker Flags",
            "difficultyLevel": "Medium",
            "points": 15,
            "author": admin_user['username'],
            "tags": ["docker", "flags", "detached"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The '-d' flag runs the container in detached mode (in the background).",
            "relatedChallengeIds": []
        },
        
        # Challenge 9 - Medium
        {
            "question": "What Docker run flag is used to map a host port to a container port?",
            "maxAttempts": 2,
            "room": docker_room,
            "description": "This challenge tests your knowledge of port mapping in Docker.",
            "hints": ["Think about publishing or exposing ports.", "It's a single letter flag."],
            "flags": [
                {
                    "flag": "-p",
                    "surroundWithTag": False,
                    "caseSensitive": True,
                    "weight": 1
                }
            ],
            "category": "Docker Networking",
            "difficultyLevel": "Medium",
            "points": 15,
            "author": admin_user['username'],
            "tags": ["docker", "networking", "ports"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The '-p' flag maps a host port to a container port (e.g., -p 8080:80).",
            "relatedChallengeIds": []
        },
        
        # Challenge 10 - Medium
        {
            "question": "What command is used to execute a command inside a running Docker container?",
            "maxAttempts": 2,
            "room": docker_room,
            "description": "This challenge tests your knowledge of executing commands in containers.",
            "hints": ["Think about running additional processes in a container.", "Often used with -it flags."],
            "flags": [
                {
                    "flag": "docker exec",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Commands",
            "difficultyLevel": "Medium",
            "points": 15,
            "author": admin_user['username'],
            "tags": ["docker", "exec", "commands"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'docker exec' command runs a command in a running container.",
            "relatedChallengeIds": []
        },
        
        # Challenge 11 - Medium
        {
            "question": "What command shows the logs of a Docker container?",
            "maxAttempts": 2,
            "room": docker_room,
            "description": "This challenge tests your knowledge of viewing container logs.",
            "hints": ["Think about debugging and monitoring containers.", "Used to see container output."],
            "flags": [
                {
                    "flag": "docker logs",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Debugging",
            "difficultyLevel": "Medium",
            "points": 15,
            "author": admin_user['username'],
            "tags": ["docker", "logs", "debugging"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'docker logs' command fetches the logs of a container.",
            "relatedChallengeIds": []
        },
        
        # Challenge 12 - Medium
        {
            "question": "What command is used to create a Docker volume?",
            "maxAttempts": 2,
            "room": docker_room,
            "description": "This challenge tests your knowledge of Docker volumes.",
            "hints": ["Volumes are used for persistent data storage.", "Check the volume subcommand."],
            "flags": [
                {
                    "flag": "docker volume create",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Storage",
            "difficultyLevel": "Medium",
            "points": 15,
            "author": admin_user['username'],
            "tags": ["docker", "volumes", "storage"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'docker volume create' command creates a new Docker volume.",
            "relatedChallengeIds": []
        },
        
        # Challenge 13 - Medium
        {
            "question": "What command lists all Docker volumes?",
            "maxAttempts": 2,
            "room": docker_room,
            "description": "This challenge tests your knowledge of listing Docker volumes.",
            "hints": ["Similar to listing containers and images.", "Use the volume subcommand."],
            "flags": [
                {
                    "flag": "docker volume ls",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Storage",
            "difficultyLevel": "Medium",
            "points": 15,
            "author": admin_user['username'],
            "tags": ["docker", "volumes", "list"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'docker volume ls' command lists all Docker volumes on the system.",
            "relatedChallengeIds": []
        },
        
        # Challenge 14 - Medium
        {
            "question": "What command is used to create a Docker network?",
            "maxAttempts": 2,
            "room": docker_room,
            "description": "This challenge tests your knowledge of Docker networking.",
            "hints": ["Networks allow containers to communicate.", "Use the network subcommand."],
            "flags": [
                {
                    "flag": "docker network create",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Networking",
            "difficultyLevel": "Medium",
            "points": 15,
            "author": admin_user['username'],
            "tags": ["docker", "networking", "create"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'docker network create' command creates a new Docker network.",
            "relatedChallengeIds": []
        },
        
        # Challenge 15 - Hard
        {
            "question": "What is the default Docker network driver?",
            "maxAttempts": 1,
            "room": docker_room,
            "description": "This challenge tests your knowledge of Docker network drivers.",
            "hints": ["Think about the most common network type.", "It's used for single-host networking."],
            "flags": [
                {
                    "flag": "bridge",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Networking",
            "difficultyLevel": "Hard",
            "points": 25,
            "author": admin_user['username'],
            "tags": ["docker", "networking", "drivers"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'bridge' network driver is the default driver for Docker containers.",
            "relatedChallengeIds": []
        },
        
        # Challenge 16 - Hard
        {
            "question": "What Dockerfile instruction is used to set the working directory?",
            "maxAttempts": 1,
            "room": docker_room,
            "description": "This challenge tests your knowledge of Dockerfile instructions.",
            "hints": ["Think about changing directories in the container.", "It's similar to the 'cd' command."],
            "flags": [
                {
                    "flag": "WORKDIR",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Dockerfile",
            "difficultyLevel": "Hard",
            "points": 25,
            "author": admin_user['username'],
            "tags": ["dockerfile", "instructions", "workdir"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'WORKDIR' instruction sets the working directory for subsequent instructions.",
            "relatedChallengeIds": []
        },
        
        # Challenge 17 - Hard
        {
            "question": "What Dockerfile instruction is used to copy files from the host to the container?",
            "maxAttempts": 1,
            "room": docker_room,
            "description": "This challenge tests your knowledge of file copying in Dockerfiles.",
            "hints": ["Think about transferring files.", "There are two main instructions for this."],
            "flags": [
                {
                    "flag": "COPY",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 0.5
                },
                {
                    "flag": "ADD",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 0.5
                }
            ],
            "category": "Dockerfile",
            "difficultyLevel": "Hard",
            "points": 25,
            "author": admin_user['username'],
            "tags": ["dockerfile", "copy", "add"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "Both 'COPY' and 'ADD' can copy files, but COPY is preferred for simple file copying.",
            "relatedChallengeIds": []
        },
        
        # Challenge 18 - Hard
        {
            "question": "What Dockerfile instruction specifies the default command to run when the container starts?",
            "maxAttempts": 1,
            "room": docker_room,
            "description": "This challenge tests your knowledge of container startup commands.",
            "hints": ["Think about what happens when a container starts.", "There are two main instructions for this."],
            "flags": [
                {
                    "flag": "CMD",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 0.6
                },
                {
                    "flag": "ENTRYPOINT",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 0.4
                }
            ],
            "category": "Dockerfile",
            "difficultyLevel": "Hard",
            "points": 25,
            "author": admin_user['username'],
            "tags": ["dockerfile", "cmd", "entrypoint"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "CMD provides defaults for executing a container, while ENTRYPOINT configures the container as an executable.",
            "relatedChallengeIds": []
        },
        
        # Challenge 19 - Medium
        {
            "question": "What command pulls a Docker image from a registry?",
            "maxAttempts": 2,
            "room": docker_room,
            "description": "This challenge tests your knowledge of downloading Docker images.",
            "hints": ["Think about downloading from Docker Hub.", "Similar to git pull."],
            "flags": [
                {
                    "flag": "docker pull",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Registry",
            "difficultyLevel": "Medium",
            "points": 15,
            "author": admin_user['username'],
            "tags": ["docker", "pull", "registry"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'docker pull' command downloads an image from a Docker registry.",
            "relatedChallengeIds": []
        },
        
        # Challenge 20 - Medium
        {
            "question": "What command pushes a Docker image to a registry?",
            "maxAttempts": 2,
            "room": docker_room,
            "description": "This challenge tests your knowledge of uploading Docker images.",
            "hints": ["Think about uploading to Docker Hub.", "Similar to git push."],
            "flags": [
                {
                    "flag": "docker push",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Registry",
            "difficultyLevel": "Medium",
            "points": 15,
            "author": admin_user['username'],
            "tags": ["docker", "push", "registry"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'docker push' command uploads an image to a Docker registry.",
            "relatedChallengeIds": []
        },
        
        # Challenge 21 - Easy
        {
            "question": "What command shows detailed information about a Docker container?",
            "maxAttempts": 3,
            "room": docker_room,
            "description": "This challenge tests your knowledge of inspecting Docker containers.",
            "hints": ["Think about examining container details.", "Used for debugging and information gathering."],
            "flags": [
                {
                    "flag": "docker inspect",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Commands",
            "difficultyLevel": "Easy",
            "points": 10,
            "author": admin_user['username'],
            "tags": ["docker", "inspect", "information"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'docker inspect' command returns detailed information about Docker objects.",
            "relatedChallengeIds": []
        },
        
        # Challenge 22 - Medium
        {
            "question": "What Docker run flag allows you to automatically remove the container when it exits?",
            "maxAttempts": 2,
            "room": docker_room,
            "description": "This challenge tests your knowledge of automatic container cleanup.",
            "hints": ["Think about temporary containers.", "Useful for testing and one-time runs."],
            "flags": [
                {
                    "flag": "--rm",
                    "surroundWithTag": False,
                    "caseSensitive": True,
                    "weight": 1
                }
            ],
            "category": "Docker Flags",
            "difficultyLevel": "Medium",
            "points": 15,
            "author": admin_user['username'],
            "tags": ["docker", "flags", "cleanup"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The '--rm' flag automatically removes the container when it exits.",
            "relatedChallengeIds": []
        },
        
        # Challenge 23 - Hard
        {
            "question": "What is the Docker build context?",
            "maxAttempts": 1,
            "room": docker_room,
            "description": "This challenge tests your understanding of Docker build concepts.",
            "hints": ["Think about what files Docker can access during build.", "It's specified as the last argument to docker build."],
            "flags": [
                {
                    "flag": "build context",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 0.7
                },
                {
                    "flag": "context",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 0.3
                }
            ],
            "category": "Docker Build",
            "difficultyLevel": "Hard",
            "points": 25,
            "author": admin_user['username'],
            "tags": ["docker", "build", "context"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The build context is the set of files and directories that Docker can access during the build process.",
            "relatedChallengeIds": []
        },
        
        # Challenge 24 - Medium
        {
            "question": "What command shows real-time resource usage statistics for Docker containers?",
            "maxAttempts": 2,
            "room": docker_room,
            "description": "This challenge tests your knowledge of monitoring Docker containers.",
            "hints": ["Think about system monitoring.", "Similar to the Unix 'top' command."],
            "flags": [
                {
                    "flag": "docker stats",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Monitoring",
            "difficultyLevel": "Medium",
            "points": 15,
            "author": admin_user['username'],
            "tags": ["docker", "monitoring", "stats"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'docker stats' command displays real-time resource usage statistics for containers.",
            "relatedChallengeIds": []
        },
        
        # Challenge 25 - Medium
        {
            "question": "What Docker run flag is used to set environment variables?",
            "maxAttempts": 2,
            "room": docker_room,
            "description": "This challenge tests your knowledge of setting environment variables in containers.",
            "hints": ["Think about configuring the container environment.", "Used with key=value pairs."],
            "flags": [
                {
                    "flag": "-e",
                    "surroundWithTag": False,
                    "caseSensitive": True,
                    "weight": 0.6
                },
                {
                    "flag": "--env",
                    "surroundWithTag": False,
                    "caseSensitive": True,
                    "weight": 0.4
                }
            ],
            "category": "Docker Configuration",
            "difficultyLevel": "Medium",
            "points": 15,
            "author": admin_user['username'],
            "tags": ["docker", "environment", "variables"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The '-e' or '--env' flag sets environment variables in the container.",
            "relatedChallengeIds": []
        },
        
        # Challenge 26 - Hard
        {
            "question": "What is the difference between CMD and ENTRYPOINT in a Dockerfile?",
            "maxAttempts": 1,
            "room": docker_room,
            "description": "This challenge tests your deep understanding of Dockerfile instructions.",
            "hints": ["Think about override behavior.", "One can be overridden, the other cannot."],
            "flags": [
                {
                    "flag": "CMD can be overridden, ENTRYPOINT cannot",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 0.6
                },
                {
                    "flag": "ENTRYPOINT is fixed, CMD is default",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 0.4
                }
            ],
            "category": "Dockerfile",
            "difficultyLevel": "Hard",
            "points": 30,
            "author": admin_user['username'],
            "tags": ["dockerfile", "cmd", "entrypoint", "difference"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "CMD provides defaults that can be overridden, while ENTRYPOINT configures the container as an executable that cannot be overridden.",
            "relatedChallengeIds": []
        },
        
        # Challenge 27 - Medium
        {
            "question": "What command is used to tag a Docker image?",
            "maxAttempts": 2,
            "room": docker_room,
            "description": "This challenge tests your knowledge of Docker image tagging.",
            "hints": ["Think about labeling images.", "Often used before pushing to registries."],
            "flags": [
                {
                    "flag": "docker tag",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Images",
            "difficultyLevel": "Medium",
            "points": 15,
            "author": admin_user['username'],
            "tags": ["docker", "tag", "images"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'docker tag' command creates a tag that refers to an existing image.",
            "relatedChallengeIds": []
        },
        
        # Challenge 28 - Easy
        {
            "question": "What command restarts a Docker container?",
            "maxAttempts": 3,
            "room": docker_room,
            "description": "This challenge tests your knowledge of restarting Docker containers.",
            "hints": ["Think about stopping and starting a container.", "Useful for applying configuration changes."],
            "flags": [
                {
                    "flag": "docker restart",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Commands",
            "difficultyLevel": "Easy",
            "points": 10,
            "author": admin_user['username'],
            "tags": ["docker", "restart", "containers"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'docker restart' command stops and then starts a container.",
            "relatedChallengeIds": []
        },
        
        # Challenge 29 - Medium
        {
            "question": "What Docker run flag is used to mount a volume?",
            "maxAttempts": 2,
            "room": docker_room,
            "description": "This challenge tests your knowledge of Docker volume mounting.",
            "hints": ["Think about persistent storage.", "Used to share data between host and container."],
            "flags": [
                {
                    "flag": "-v",
                    "surroundWithTag": False,
                    "caseSensitive": True,
                    "weight": 0.6
                },
                {
                    "flag": "--volume",
                    "surroundWithTag": False,
                    "caseSensitive": True,
                    "weight": 0.4
                }
            ],
            "category": "Docker Storage",
            "difficultyLevel": "Medium",
            "points": 15,
            "author": admin_user['username'],
            "tags": ["docker", "volumes", "mount"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The '-v' or '--volume' flag mounts a volume or bind mount into the container.",
            "relatedChallengeIds": []
        },
        
        # Challenge 30 - Hard
        {
            "question": "What is the default restart policy for Docker containers?",
            "maxAttempts": 1,
            "room": docker_room,
            "description": "This challenge tests your knowledge of Docker restart policies.",
            "hints": ["Think about what happens when a container stops.", "Most basic policy."],
            "flags": [
                {
                    "flag": "no",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Configuration",
            "difficultyLevel": "Hard",
            "points": 25,
            "author": admin_user['username'],
            "tags": ["docker", "restart", "policy"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The default restart policy is 'no', meaning containers don't restart automatically.",
            "relatedChallengeIds": []
        },
        
        # Challenge 31 - Medium
        {
            "question": "What command is used to pause a running Docker container?",
            "maxAttempts": 2,
            "room": docker_room,
            "description": "This challenge tests your knowledge of pausing Docker containers.",
            "hints": ["Think about temporarily stopping processes.", "Different from stopping the container."],
            "flags": [
                {
                    "flag": "docker pause",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Commands",
            "difficultyLevel": "Medium",
            "points": 15,
            "author": admin_user['username'],
            "tags": ["docker", "pause", "containers"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'docker pause' command suspends all processes in the specified container.",
            "relatedChallengeIds": []
        },
        
        # Challenge 32 - Medium
        {
            "question": "What command is used to unpause a paused Docker container?",
            "maxAttempts": 2,
            "room": docker_room,
            "description": "This challenge tests your knowledge of unpausing Docker containers.",
            "hints": ["Opposite of pause.", "Resumes suspended processes."],
            "flags": [
                {
                    "flag": "docker unpause",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Docker Commands",
            "difficultyLevel": "Medium",
            "points": 15,
            "author": admin_user['username'],
            "tags": ["docker", "unpause", "containers"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'docker unpause' command unpauses all processes in the specified container.",
            "relatedChallengeIds": []
        },
        
        # Challenge 33 - Hard
        {
            "question": "What Dockerfile instruction is used to expose ports?",
            "maxAttempts": 1,
            "room": docker_room,
            "description": "This challenge tests your knowledge of port exposure in Dockerfiles.",
            "hints": ["Think about network accessibility.", "Informs Docker that the container listens on specific ports."],
            "flags": [
                {
                    "flag": "EXPOSE",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 1
                }
            ],
            "category": "Dockerfile",
            "difficultyLevel": "Hard",
            "points": 25,
            "author": admin_user['username'],
            "tags": ["dockerfile", "expose", "ports"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "The 'EXPOSE' instruction informs Docker that the container listens on specified network ports at runtime.",
            "relatedChallengeIds": []
        },
        
        # Challenge 34 - Expert
        {
            "question": "What is a Docker multi-stage build?",
            "maxAttempts": 1,
            "room": docker_room,
            "description": "This challenge tests your advanced knowledge of Docker build optimization.",
            "hints": ["Think about reducing image size.", "Uses multiple FROM statements."],
            "flags": [
                {
                    "flag": "multiple FROM statements",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 0.4
                },
                {
                    "flag": "build optimization",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 0.3
                },
                {
                    "flag": "reduce image size",
                    "surroundWithTag": False,
                    "caseSensitive": False,
                    "weight": 0.3
                }
            ],
            "category": "Docker Advanced",
            "difficultyLevel": "Expert",
            "points": 35,
            "author": admin_user['username'],
            "tags": ["docker", "multi-stage", "build", "optimization"],
            "visible": True,
            "expirationDate": (datetime.now() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S'),
            "attachments": [],
            "solutionExplanation": "Multi-stage builds use multiple FROM statements to create smaller, more efficient images by copying only necessary artifacts between stages.",
            "relatedChallengeIds": []
        }
    ]