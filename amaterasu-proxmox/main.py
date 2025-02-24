import time
import urllib3
import requests
from dotenv import load_dotenv
import os

# Suppress InsecureRequestWarning
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# Proxmox API details
PROXMOX_HOST = "https://10.0.0.250:8006"
TOKEN_ID = os.getenv("TOKEN_ID")
TOKEN_SECRET = os.getenv("TOKEN_SECRET")
HEADERS = {
    "Authorization": f"PVEAPIToken={TOKEN_ID}={TOKEN_SECRET}",
    "Content-Type": "application/json",
}
NODE = "inferno"
TEAM_ID = "185CPT"


def clone_vm(source_vmid: int, new_vmid: int, name: str, description: str):
    """Clones a VM in Proxmox."""
    url = f"{PROXMOX_HOST}/api2/json/nodes/{NODE}/qemu/{source_vmid}/clone"
    payload = {"newid": new_vmid, "name": name, "description": description}

    response = requests.post(url, json=payload, headers=HEADERS, verify=False)
    if response.status_code == 200:
        print(f"VM {name} cloned successfully:", response.json())
        return True
    else:
        print(f"Error cloning VM {name}:", response.text)
        return False


def start_vm(vmid: int):
    """Starts a VM in Proxmox."""
    url = f"{PROXMOX_HOST}/api2/json/nodes/{NODE}/qemu/{vmid}/status/start"
    payload = {"vmid": vmid, "node": NODE}

    response = requests.post(url, headers=HEADERS, json=payload, verify=False)

    if response.status_code == 200:
        print(f"VM {vmid} started successfully:", response.json())
        return True
    else:
        print(f"Error starting VM {vmid}:", response.text)
        return False


def stop_vm(vmid: int):
    """Stops a VM in Proxmox."""
    url = f"{PROXMOX_HOST}/api2/json/nodes/{NODE}/qemu/{vmid}/status/stop"

    payload = {"node": NODE, "vmid": vmid}
    response = requests.post(url, headers=HEADERS, json=payload, verify=False)
    if response.status_code == 200:
        print(f"VM {vmid} stopped successfully:", response.json())
        return True
    else:
        print(f"Error stopping VM {vmid}:", response.text)
        return False


def delete_vm(vmid: int):
    """Deletes a VM in Proxmox."""
    url = f"{PROXMOX_HOST}/api2/json/nodes/{NODE}/qemu/{vmid}"
    payload = {"node": NODE, "vmid": vmid}
    response = requests.delete(url, headers=HEADERS, verify=False)

    if response.status_code == 200:
        print(f"VM {vmid} deleted successfully:", response.json())
        return True
    else:
        print(
            f"Error deleting VM {vmid} (Status {response.status_code}):", response.text
        )
        return False


def get_vms():
    url = f"{PROXMOX_HOST}/api2/json/nodes/{NODE}/qemu"
    response = requests.get(url, headers=HEADERS, verify=False)
    return response.json()


def get_vmids_with_short_uptime_and_high_vmid(
    uptime_threshold_hours=10, vmid_threshold=120
):
    """
    Returns a list of VMIDs where the uptime is less than the specified threshold
    AND the VMID is greater than the specified threshold.
    """
    try:
        data = get_vms()
        if data and "data" in data:
            vmids = []
            for vm in data["data"]:
                if "uptime" in vm and "vmid" in vm:
                    uptime_seconds = vm["uptime"]
                    uptime_hours = uptime_seconds / 3600  # Convert seconds to hours
                    vmid = vm["vmid"]

                    if uptime_hours < uptime_threshold_hours and vmid > vmid_threshold:
                        vmids.append(vmid)
            return vmids
        else:
            print("Error: No 'data' found in the API response, or response is empty.")
            return []
    except requests.exceptions.RequestException as e:
        print(f"Error during API request: {e}")
        return []
    except Exception as e:
        print(f"An unexpected error occurred: {e}")
        return []


def main():
    """Manages the VM lifecycle: clone -> start -> stop -> delete."""
    lab = {102: "lab1-windows", 104: "lab1-kali", 105: "lab1-dvwa", 106: "lab1-vyos"}

    INIT_ID = 500

    for vmid, vmname in lab.items():
        new_vmid = INIT_ID
        new_name = f"{TEAM_ID}-{vmname}"

        print(f"Cloning {new_name}...")

        if clone_vm(vmid, new_vmid, new_name, f"{vmname} for {TEAM_ID}"):
            print(f"Success for {TEAM_ID} on {new_name} ({new_vmid})")

            time.sleep(10)  # Wait 10 seconds before starting

            print(f"Starting {new_name}...")
            if start_vm(new_vmid):
                time.sleep(10)  # Wait 10 seconds before stopping

                print(f"Stopping {new_name}...")
                if stop_vm(new_vmid):
                    time.sleep(10)  # Wait 10 seconds before deleting

                    print(f"Deleting {new_name}...")
                    delete_vm(new_vmid)
            INIT_ID += 1


def quick_delete(x):
    for y in x:
        stop_vm(y)
    for y in x:
        delete_vm(y)


if __name__ == "__main__":
    # main()
    quick_delete(get_vmids_with_short_uptime_and_high_vmid())
    # print(get_vms())
