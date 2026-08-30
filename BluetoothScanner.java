// BluetoothScanner.java
import javax.bluetooth.*;
import javax.microedition.io.Connector;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class BluetoothScanner implements DiscoveryListener {
    private int timeout;
    private Pattern filter;
    private List<DeviceInfo> devices = new ArrayList<>();
    private final Object lock = new Object();
    private boolean scanFinished = false;

    static class DeviceInfo {
        String address, name, timestamp;
        int rssi;
        List<String> services;
    }

    public BluetoothScanner(int timeout, String filterPattern) {
        this.timeout = timeout;
        if (filterPattern != null) this.filter = Pattern.compile(filterPattern);
    }

    public void scan() throws BluetoothStateException {
        LocalDevice local = LocalDevice.getLocalDevice();
        DiscoveryAgent agent = local.getDiscoveryAgent();
        synchronized (lock) {
            agent.startInquiry(DiscoveryAgent.GIAC, this);
            try {
                lock.wait(timeout * 1000L);
            } catch (InterruptedException e) {}
            agent.cancelInquiry(this);
        }
        scanFinished = true;
    }

    @Override
    public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
        try {
            String name = btDevice.getFriendlyName(false);
            if (filter != null && name != null && !filter.matcher(name).find()) return;
            DeviceInfo info = new DeviceInfo();
            info.address = btDevice.getBluetoothAddress();
            info.name = name == null ? "Unknown" : name;
            info.rssi = 0; // not directly available via JSR-82
            info.timestamp = Instant.now().toString();
            info.services = new ArrayList<>();
            devices.add(info);
        } catch (IOException ignored) {}
    }

    @Override public void inquiryCompleted(int discType) { synchronized (lock) { lock.notify(); } }
    @Override public void serviceSearchCompleted(int transID, int respCode) {}
    @Override public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {}

    public void printResults() {
        if (devices.isEmpty()) {
            System.out.println("No devices found.");
            return;
        }
        System.out.println("Found " + devices.size() + " device(s):");
        for (DeviceInfo d : devices) {
            System.out.printf("  %s (%s) RSSI: %d dBm%n", d.name, d.address, d.rssi);
        }
    }

    public void exportJson(String filename) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.write(Paths.get(filename), gson.toJson(devices).getBytes());
        System.out.println("Exported to " + filename + " (JSON)");
    }

    public void exportCsv(String filename) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("timestamp,name,address,rssi,services");
            for (DeviceInfo d : devices) {
                String services = String.join("; ", d.services);
                pw.printf("%s,\"%s\",%s,%d,\"%s\"%n", d.timestamp, d.name, d.address, d.rssi, services);
            }
        }
        System.out.println("Exported to " + filename + " (CSV)");
    }

    public void exportTxt(String filename) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            for (DeviceInfo d : devices) {
                String services = String.join(", ", d.services);
                pw.printf("%s | %s | %s | RSSI: %d dBm | Services: %s%n",
                    d.timestamp, d.name, d.address, d.rssi, services);
            }
        }
        System.out.println("Exported to " + filename + " (TXT)");
    }

    public static void main(String[] args) {
        int timeout = 5;
        String filter = null;
        String exportJson = null, exportCsv = null, exportTxt = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--timeout": timeout = Integer.parseInt(args[++i]); break;
                case "--filter": filter = args[++i]; break;
                case "--export-json": exportJson = args[++i]; break;
                case "--export-csv": exportCsv = args[++i]; break;
                case "--export-txt": exportTxt = args[++i]; break;
            }
        }

        try {
            BluetoothScanner scanner = new BluetoothScanner(timeout, filter);
            scanner.scan();
            scanner.printResults();
            if (exportJson != null) scanner.exportJson(exportJson);
            if (exportCsv != null) scanner.exportCsv(exportCsv);
            if (exportTxt != null) scanner.exportTxt(exportTxt);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
