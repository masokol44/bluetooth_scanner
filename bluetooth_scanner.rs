// bluetooth_scanner.rs
use btleplug::api::{Central, Manager as _, Peripheral, ScanFilter};
use btleplug::platform::Manager;
use clap::{App, Arg};
use regex::Regex;
use serde::{Deserialize, Serialize};
use std::fs;
use std::time::{Duration, Instant};
use tokio::time;

#[derive(Serialize, Deserialize, Clone)]
struct DeviceInfo {
    address: String,
    name: String,
    rssi: i16,
    timestamp: String,
    services: Vec<String>,
}

struct Scanner {
    timeout: u64,
    filter: Option<Regex>,
    devices: Vec<DeviceInfo>,
}

impl Scanner {
    fn new(timeout: u64, filter: Option<&str>) -> Self {
        let re = filter.map(|f| Regex::new(f).unwrap());
        Scanner { timeout, filter: re, devices: Vec::new() }
    }

    async fn scan(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        let manager = Manager::new().await?;
        let adapter = manager.adapters().await?.into_iter().next().unwrap();
        adapter.start_scan(ScanFilter::default()).await?;

        let start = Instant::now();
        while start.elapsed().as_secs() < self.timeout {
            if let Ok(peripherals) = adapter.peripherals().await {
                for p in peripherals {
                    if let Some(prop) = p.properties().await? {
                        let name = prop.local_name.unwrap_or_else(|| "Unknown".to_string());
                        if let Some(ref re) = self.filter {
                            if !re.is_match(&name) {
                                continue;
                            }
                        }
                        let info = DeviceInfo {
                            address: p.address().to_string(),
                            name,
                            rssi: prop.rssi.unwrap_or(0),
                            timestamp: chrono::Utc::now().to_rfc3339(),
                            services: prop.services.iter().map(|s| s.to_string()).collect(),
                        };
                        if !self.devices.iter().any(|d| d.address == info.address) {
                            self.devices.push(info);
                        }
                    }
                }
            }
            time::sleep(Duration::from_millis(500)).await;
        }
        adapter.stop_scan().await?;
        Ok(())
    }

    fn print_results(&self) {
        if self.devices.is_empty() {
            println!("No devices found.");
            return;
        }
        println!("Found {} device(s):", self.devices.len());
        for d in &self.devices {
            println!("  {} ({}) RSSI: {} dBm", d.name, d.address, d.rssi);
        }
    }

    fn export_json(&self, filename: &str) -> Result<(), Box<dyn std::error::Error>> {
        let json = serde_json::to_string_pretty(&self.devices)?;
        fs::write(filename, json)?;
        println!("Exported to {} (JSON)", filename);
        Ok(())
    }

    fn export_csv(&self, filename: &str) -> Result<(), Box<dyn std::error::Error>> {
        let mut wtr = csv::Writer::from_path(filename)?;
        wtr.write_record(&["timestamp", "name", "address", "rssi", "services"])?;
        for d in &self.devices {
            let services = d.services.join("; ");
            wtr.write_record(&[&d.timestamp, &d.name, &d.address, &d.rssi.to_string(), &services])?;
        }
        wtr.flush()?;
        println!("Exported to {} (CSV)", filename);
        Ok(())
    }

    fn export_txt(&self, filename: &str) -> Result<(), Box<dyn std::error::Error>> {
        let mut content = String::new();
        for d in &self.devices {
            let svc = d.services.join(", ");
            content.push_str(&format!("{} | {} | {} | RSSI: {} dBm | Services: {}\n",
                d.timestamp, d.name, d.address, d.rssi, svc));
        }
        fs::write(filename, content)?;
        println!("Exported to {} (TXT)", filename);
        Ok(())
    }
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let matches = App::new("Bluetooth Scanner")
        .arg(Arg::with_name("timeout").long("timeout").takes_value(true).default_value("5"))
        .arg(Arg::with_name("filter").long("filter").takes_value(true))
        .arg(Arg::with_name("export-json").long("export-json").takes_value(true))
        .arg(Arg::with_name("export-csv").long("export-csv").takes_value(true))
        .arg(Arg::with_name("export-txt").long("export-txt").takes_value(true))
        .get_matches();

    let timeout: u64 = matches.value_of("timeout").unwrap().parse()?;
    let filter = matches.value_of("filter");
    let mut scanner = Scanner::new(timeout, filter);
    scanner.scan().await?;
    scanner.print_results();

    if let Some(file) = matches.value_of("export-json") {
        scanner.export_json(file)?;
    }
    if let Some(file) = matches.value_of("export-csv") {
        scanner.export_csv(file)?;
    }
    if let Some(file) = matches.value_of("export-txt") {
        scanner.export_txt(file)?;
    }
    Ok(())
}
