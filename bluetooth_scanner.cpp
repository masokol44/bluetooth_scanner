// bluetooth_scanner.cpp
#include <iostream>
#include <string>
#include <vector>
#include <regex>
#include <fstream>
#include <json/json.h> // using jsoncpp
#include <bluetooth/bluetooth.h>
#include <bluetooth/hci.h>
#include <bluetooth/hci_lib.h>
#include <unistd.h>

using namespace std;

struct DeviceInfo {
    string address;
    string name;
    int rssi;
    string timestamp;
    vector<string> services;
};

vector<DeviceInfo> scanDevices(int timeout, const string& filterPattern) {
    vector<DeviceInfo> devices;
    regex filter;
    bool hasFilter = !filterPattern.empty();
    if (hasFilter) filter = regex(filterPattern, regex::icase);

    int dev_id = hci_get_route(nullptr);
    int sock = hci_open_dev(dev_id);
    if (sock < 0) {
        cerr << "Failed to open HCI socket" << endl;
        return devices;
    }

    int len = 8;
    int max_rsp = 255;
    char buf[HCI_MAX_EVENT_SIZE];
    struct hci_inquiry_req ir = { 0 };
    ir.dev_id = dev_id;
    ir.num_rsp = max_rsp;
    ir.length = timeout;
    ir.flags = IREQ_CACHE_FLUSH;

    auto rsps = (inquiry_info*)malloc(max_rsp * sizeof(inquiry_info));
    int count = hci_inquiry(dev_id, len, max_rsp, nullptr, &rsps, IREQ_CACHE_FLUSH);
    if (count < 0) {
        perror("hci_inquiry");
        free(rsps);
        close(sock);
        return devices;
    }

    for (int i = 0; i < count; i++) {
        char addr[19];
        ba2str(&rsps[i].bdaddr, addr);
        char name[248] = {0};
        if (hci_read_remote_name(sock, &rsps[i].bdaddr, sizeof(name), name, 0) < 0)
            strcpy(name, "Unknown");

        if (hasFilter && !regex_search(name, filter))
            continue;

        DeviceInfo info;
        info.address = addr;
        info.name = name;
        info.rssi = rsps[i].rssi;
        info.timestamp = to_string(time(nullptr)); // simplified
        // services not available in basic scan
        devices.push_back(info);
    }

    free(rsps);
    close(sock);
    return devices;
}

void printResults(const vector<DeviceInfo>& devices) {
    if (devices.empty()) {
        cout << "No devices found." << endl;
        return;
    }
    cout << "Found " << devices.size() << " device(s):" << endl;
    for (const auto& d : devices) {
        cout << "  " << d.name << " (" << d.address << ") RSSI: " << d.rssi << " dBm" << endl;
    }
}

void exportJSON(const vector<DeviceInfo>& devices, const string& filename) {
    Json::Value root(Json::arrayValue);
    for (const auto& d : devices) {
        Json::Value item;
        item["address"] = d.address;
        item["name"] = d.name;
        item["rssi"] = d.rssi;
        item["timestamp"] = d.timestamp;
        root.append(item);
    }
    ofstream ofs(filename);
    ofs << root.toStyledString();
    cout << "Exported to " << filename << " (JSON)" << endl;
}

void exportCSV(const vector<DeviceInfo>& devices, const string& filename) {
    ofstream ofs(filename);
    ofs << "timestamp,name,address,rssi\n";
    for (const auto& d : devices) {
        ofs << d.timestamp << "," << d.name << "," << d.address << "," << d.rssi << "\n";
    }
    cout << "Exported to " << filename << " (CSV)" << endl;
}

void exportTXT(const vector<DeviceInfo>& devices, const string& filename) {
    ofstream ofs(filename);
    for (const auto& d : devices) {
        ofs << d.timestamp << " | " << d.name << " | " << d.address << " | RSSI: " << d.rssi << " dBm\n";
    }
    cout << "Exported to " << filename << " (TXT)" << endl;
}

int main(int argc, char* argv[]) {
    int timeout = 5;
    string filter, exportJson, exportCsv, exportTxt;

    for (int i = 1; i < argc; ++i) {
        string arg = argv[i];
        if (arg == "--timeout" && i+1 < argc) timeout = stoi(argv[++i]);
        else if (arg == "--filter" && i+1 < argc) filter = argv[++i];
        else if (arg == "--export-json" && i+1 < argc) exportJson = argv[++i];
        else if (arg == "--export-csv" && i+1 < argc) exportCsv = argv[++i];
        else if (arg == "--export-txt" && i+1 < argc) exportTxt = argv[++i];
    }

    auto devices = scanDevices(timeout, filter);
    printResults(devices);

    if (!exportJson.empty()) exportJSON(devices, exportJson);
    if (!exportCsv.empty()) exportCSV(devices, exportCsv);
    if (!exportTxt.empty()) exportTXT(devices, exportTxt);

    return 0;
}
