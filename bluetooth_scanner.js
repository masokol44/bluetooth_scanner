#!/usr/bin/env node
// bluetooth_scanner.js
const { program } = require('commander');
const { spawn } = require('child_process');
const fs = require('fs');
const chalk = require('chalk');

class BluetoothScanner {
    constructor(timeout = 5, filter = null) {
        this.timeout = timeout;
        this.filter = filter ? new RegExp(filter) : null;
        this.devices = [];
    }

    async scan() {
        return new Promise((resolve) => {
            const noble = require('@abandonware/noble');
            noble.on('discover', (device) => {
                if (this.filter && device.localName && !this.filter.test(device.localName)) return;
                this.devices.push({
                    address: device.address,
                    name: device.localName || 'Unknown',
                    rssi: device.rssi,
                    timestamp: new Date().toISOString(),
                    services: device.advertisement.serviceUuids || []
                });
            });

            noble.startScanning([], true);
            setTimeout(() => {
                noble.stopScanning();
                resolve(this.devices);
            }, this.timeout * 1000);
        });
    }

    printResults() {
        if (this.devices.length === 0) {
            console.log(chalk.yellow('No devices found.'));
            return;
        }
        console.log(chalk.cyan(`Found ${this.devices.length} device(s):`));
        for (const d of this.devices) {
            console.log(chalk.green(`  ${d.name} (${d.address}) RSSI: ${d.rssi} dBm`));
        }
    }

    exportJson(filename) {
        fs.writeFileSync(filename, JSON.stringify(this.devices, null, 2));
        console.log(chalk.green(`Exported to ${filename} (JSON)`));
    }

    exportCsv(filename) {
        const header = 'timestamp,name,address,rssi,services\n';
        const rows = this.devices.map(d =>
            `${d.timestamp},${d.name},${d.address},${d.rssi},"${d.services.join('; ')}"`
        ).join('\n');
        fs.writeFileSync(filename, header + rows);
        console.log(chalk.green(`Exported to ${filename} (CSV)`));
    }

    exportTxt(filename) {
        const lines = this.devices.map(d =>
            `${d.timestamp} | ${d.name} | ${d.address} | RSSI: ${d.rssi} dBm | Services: ${d.services.join(', ')}`
        );
        fs.writeFileSync(filename, lines.join('\n'));
        console.log(chalk.green(`Exported to ${filename} (TXT)`));
    }
}

program
    .option('-t, --timeout <seconds>', 'Scan duration', parseInt, 5)
    .option('-f, --filter <regex>', 'Filter by name')
    .option('--export-json <file>', 'Export to JSON')
    .option('--export-csv <file>', 'Export to CSV')
    .option('--export-txt <file>', 'Export to TXT')
    .parse(process.argv);

const opts = program.opts();

(async () => {
    const scanner = new BluetoothScanner(opts.timeout, opts.filter);
    await scanner.scan();
    scanner.printResults();
    if (opts.exportJson) scanner.exportJson(opts.exportJson);
    if (opts.exportCsv) scanner.exportCsv(opts.exportCsv);
    if (opts.exportTxt) scanner.exportTxt(opts.exportTxt);
})();
