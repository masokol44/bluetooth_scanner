// BluetoothScanner.kt
import javax.bluetooth.*
import java.io.*
import java.time.Instant
import java.util.regex.Pattern
import com.google.gson.GsonBuilder

data class DeviceInfo(val address: String, val name: String, val rssi: Int, val timestamp: String, val services: List<String>)

class BluetoothScanner(private val timeout: Int, private val filterPattern: String?) {
    private val devices = mutableListOf<DeviceInfo>()
    private val lock = Object()
    private var scanFinished = false
    private val filter = if (filterPattern != null) Pattern.compile(filterPattern) else null

    fun scan() {
        val local = LocalDevice.getLocalDevice()
        val agent = local.discoveryAgent
        synchronized(lock) {
            agent.startInquiry(DiscoveryAgent.GIAC, object : DiscoveryListener {
                override fun deviceDiscovered(btDevice: RemoteDevice, cod: DeviceClass) {
                    try {
                        val name = btDevice.getFriendlyName(false) ?: "Unknown"
                        if (filter != null && !filter.matcher(name).find()) return
                        val info = DeviceInfo(
                            address = btDevice.bluetoothAddress,
                            name = name,
                            rssi = 0,
                            timestamp = Instant.now().toString(),
                            services = emptyList()
                        )
                        devices.add(info)
                    } catch (e: IOException) {}
                }
                override fun inquiryCompleted(discType: Int) { synchronized(lock) { lock.notify() } }
                override fun serviceSearchCompleted(transID: Int, respCode: Int) {}
                override fun servicesDiscovered(transID: Int, servRecord: Array<ServiceRecord>) {}
            })
            try {
                lock.wait(timeout * 1000L)
            } catch (e: InterruptedException) {}
            agent.cancelInquiry(this)
        }
        scanFinished = true
    }

    fun printResults() {
        if (devices.isEmpty()) {
            println("No devices found.")
            return
        }
        println("Found ${devices.size} device(s):")
        for (d in devices) {
            println("  ${d.name} (${d.address}) RSSI: ${d.rssi} dBm")
        }
    }

    fun exportJson(filename: String) {
        val gson = GsonBuilder().setPrettyPrinting().create()
        File(filename).writeText(gson.toJson(devices))
        println("Exported to $filename (JSON)")
    }

    fun exportCsv(filename: String) {
        File(filename).printWriter().use { pw ->
            pw.println("timestamp,name,address,rssi,services")
            for (d in devices) {
                val services = d.services.joinToString("; ")
                pw.println("${d.timestamp},${d.name},${d.address},${d.rssi},\"$services\"")
            }
        }
        println("Exported to $filename (CSV)")
    }

    fun exportTxt(filename: String) {
        File(filename).printWriter().use { pw ->
            for (d in devices) {
                val services = d.services.joinToString(", ")
                pw.println("${d.timestamp} | ${d.name} | ${d.address} | RSSI: ${d.rssi} dBm | Services: $services")
            }
        }
        println("Exported to $filename (TXT)")
    }
}

fun main(args: Array<String>) {
    var timeout = 5
    var filter: String? = null
    var exportJson: String? = null
    var exportCsv: String? = null
    var exportTxt: String? = null

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--timeout" -> timeout = args[++i].toInt()
            "--filter" -> filter = args[++i]
            "--export-json" -> exportJson = args[++i]
            "--export-csv" -> exportCsv = args[++i]
            "--export-txt" -> exportTxt = args[++i]
        }
        i++
    }

    val scanner = BluetoothScanner(timeout, filter)
    scanner.scan()
    scanner.printResults()

    exportJson?.let { scanner.exportJson(it) }
    exportCsv?.let { scanner.exportCsv(it) }
    exportTxt?.let { scanner.exportTxt(it) }
}
