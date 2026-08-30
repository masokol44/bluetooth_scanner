
```python
#!/usr/bin/env python3
# bluetooth_scanner.py
import asyncio
import json
import csv
import re
import argparse
import sys
from datetime import datetime
from bleak import BleakScanner
from colorama import init, Fore, Style

init(autoreset=True)

class BluetoothScanner:
    def __init__(self, timeout=5, filter_pattern=None):
        self.timeout = timeout
        self.filter = re.compile(filter_pattern) if filter_pattern else None
        self.devices = []

    async def scan(self):
        def callback(device, advertisement_data):
            if self.filter and device.name and not self.filter.search(device.name):
                return
            info = {
                "address": device.address,
                "name": device.name or "Unknown",
                "rssi": advertisement_data.rssi,
                "timestamp": datetime.now().isoformat(),
                "services": [str(s) for s in advertisement_data.service_uuids] if advertisement_data.service_uuids else []
            }
            self.devices.append(info)

        scanner = BleakScanner(callback)
        await scanner.start()
        await asyncio.sleep(self.timeout)
        await scanner.stop()
        return self.devices

    def print_results(self):
        if not self.devices:
            print(Fore.YELLOW + "No devices found.")
            return
        print(Fore.CYAN + f"Found {len(self.devices)} device(s):")
        for d in self.devices:
            print(Fore.GREEN + f"  {d['name']} ({d['address']}) RSSI: {d['rssi']} dBm")

    def export_json(self, filename):
        with open(filename, 'w') as f:
            json.dump(self.devices, f, indent=2)
        print(Fore.GREEN + f"Exported to {filename} (JSON)")

    def export_csv(self, filename):
        with open(filename, 'w', newline='') as f:
            writer = csv.DictWriter(f, fieldnames=["timestamp", "name", "address", "rssi", "services"])
            writer.writeheader()
            for d in self.devices:
                writer.writerow({
                    "timestamp": d["timestamp"],
                    "name": d["name"],
                    "address": d["address"],
                    "rssi": d["rssi"],
                    "services": "; ".join(d["services"])
                })
        print(Fore.GREEN + f"Exported to {filename} (CSV)")

    def export_txt(self, filename):
        with open(filename, 'w') as f:
            for d in self.devices:
                f.write(f"{d['timestamp']} | {d['name']} | {d['address']} | RSSI: {d['rssi']} dBm | Services: {', '.join(d['services'])}\n")
        print(Fore.GREEN + f"Exported to {filename} (TXT)")

async def main():
    parser = argparse.ArgumentParser(description="Bluetooth Scanner with export")
    parser.add_argument("--timeout", type=int, default=5, help="Scan duration in seconds")
    parser.add_argument("--filter", help="Regex filter for device name")
    parser.add_argument("--export-json", help="Export to JSON file")
    parser.add_argument("--export-csv", help="Export to CSV file")
    parser.add_argument("--export-txt", help="Export to TXT file")
    args = parser.parse_args()

    scanner = BluetoothScanner(args.timeout, args.filter)
    await scanner.scan()
    scanner.print_results()

    if args.export_json:
        scanner.export_json(args.export_json)
    if args.export_csv:
        scanner.export_csv(args.export_csv)
    if args.export_txt:
        scanner.export_txt(args.export_txt)

if __name__ == "__main__":
    asyncio.run(main())
