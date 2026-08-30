// BluetoothScanner.cs
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using InTheHand.Net.Bluetooth;
using InTheHand.Net.Sockets;
using Newtonsoft.Json;

namespace BluetoothScanner
{
    class Program
    {
        static async Task Main(string[] args)
        {
            int timeout = 5;
            string filter = null;
            string exportJson = null, exportCsv = null, exportTxt = null;

            for (int i = 0; i < args.Length; i++)
            {
                switch (args[i])
                {
                    case "--timeout": timeout = int.Parse(args[++i]); break;
                    case "--filter": filter = args[++i]; break;
                    case "--export-json": exportJson = args[++i]; break;
                    case "--export-csv": exportCsv = args[++i]; break;
                    case "--export-txt": exportTxt = args[++i]; break;
                }
            }

            var scanner = new BluetoothScanner(timeout, filter);
            await scanner.ScanAsync();
            scanner.PrintResults();

            if (!string.IsNullOrEmpty(exportJson))
                scanner.ExportJson(exportJson);
            if (!string.IsNullOrEmpty(exportCsv))
                scanner.ExportCsv(exportCsv);
            if (!string.IsNullOrEmpty(exportTxt))
                scanner.ExportTxt(exportTxt);
        }
    }

    class BluetoothScanner
    {
        private int timeout;
        private string filter;
        private List<DeviceInfo> devices = new List<DeviceInfo>();

        public BluetoothScanner(int timeout, string filter)
        {
            this.timeout = timeout;
            this.filter = filter;
        }

        public async Task ScanAsync()
        {
            var client = new BluetoothClient();
            var devicesFound = await Task.Run(() => client.DiscoverDevices(timeout, false, true, true));
            foreach (var d in devicesFound)
            {
                if (!string.IsNullOrEmpty(filter) && d.DeviceName != null && !d.DeviceName.Contains(filter))
                    continue;
                var info = new DeviceInfo
                {
                    Address = d.DeviceAddress.ToString(),
                    Name = d.DeviceName ?? "Unknown",
                    Rssi = d.Rssi,
                    Timestamp = DateTime.UtcNow.ToString("o"),
                    Services = new List<string>()
                };
                devices.Add(info);
            }
        }

        public void PrintResults()
        {
            if (devices.Count == 0)
            {
                Console.WriteLine("No devices found.");
                return;
            }
            Console.WriteLine($"Found {devices.Count} device(s):");
            foreach (var d in devices)
                Console.WriteLine($"  {d.Name} ({d.Address}) RSSI: {d.Rssi} dBm");
        }

        public void ExportJson(string filename)
        {
            string json = JsonConvert.SerializeObject(devices, Formatting.Indented);
            File.WriteAllText(filename, json);
            Console.WriteLine($"Exported to {filename} (JSON)");
        }

        public void ExportCsv(string filename)
        {
            using var sw = new StreamWriter(filename);
            sw.WriteLine("timestamp,name,address,rssi,services");
            foreach (var d in devices)
            {
                string services = string.Join("; ", d.Services);
                sw.WriteLine($"{d.Timestamp},{d.Name},{d.Address},{d.Rssi},\"{services}\"");
            }
            Console.WriteLine($"Exported to {filename} (CSV)");
        }

        public void ExportTxt(string filename)
        {
            using var sw = new StreamWriter(filename);
            foreach (var d in devices)
            {
                string services = string.Join(", ", d.Services);
                sw.WriteLine($"{d.Timestamp} | {d.Name} | {d.Address} | RSSI: {d.Rssi} dBm | Services: {services}");
            }
            Console.WriteLine($"Exported to {filename} (TXT)");
        }

        class DeviceInfo
        {
            public string Address { get; set; }
            public string Name { get; set; }
            public int Rssi { get; set; }
            public string Timestamp { get; set; }
            public List<string> Services { get; set; }
        }
    }
}
