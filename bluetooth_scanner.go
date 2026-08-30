// bluetooth_scanner.go
package main

import (
	"encoding/csv"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"regexp"
	"time"

	"github.com/go-ble/ble"
	"github.com/go-ble/ble/examples/lib/dev"
)

type DeviceInfo struct {
	Address   string   `json:"address"`
	Name      string   `json:"name"`
	RSSI      int      `json:"rssi"`
	Timestamp string   `json:"timestamp"`
	Services  []string `json:"services"`
}

type Scanner struct {
	timeout  int
	filter   *regexp.Regexp
	devices  []DeviceInfo
}

func NewScanner(timeout int, filter string) *Scanner {
	var re *regexp.Regexp
	if filter != "" {
		re = regexp.MustCompile(filter)
	}
	return &Scanner{timeout: timeout, filter: re}
}

func (s *Scanner) Scan() error {
	d, err := dev.NewDevice("default")
	if err != nil {
		return err
	}
	ble.SetDefaultDevice(d)

	ch := make(chan bool)
	ble.Scan(context.Background(), true, func(adv ble.Advertisement) {
		if s.filter != nil && !s.filter.MatchString(adv.LocalName()) {
			return
		}
		info := DeviceInfo{
			Address:   adv.Addr().String(),
			Name:      adv.LocalName(),
			RSSI:      adv.RSSI(),
			Timestamp: time.Now().Format(time.RFC3339),
			Services:  adv.Services(),
		}
		s.devices = append(s.devices, info)
	}, nil)

	time.Sleep(time.Duration(s.timeout) * time.Second)
	ble.StopScan()
	return nil
}

func (s *Scanner) PrintResults() {
	if len(s.devices) == 0 {
		fmt.Println("No devices found.")
		return
	}
	fmt.Printf("Found %d device(s):\n", len(s.devices))
	for _, d := range s.devices {
		fmt.Printf("  %s (%s) RSSI: %d dBm\n", d.Name, d.Address, d.RSSI)
	}
}

func (s *Scanner) ExportJSON(filename string) error {
	data, err := json.MarshalIndent(s.devices, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(filename, data, 0644)
}

func (s *Scanner) ExportCSV(filename string) error {
	f, err := os.Create(filename)
	if err != nil {
		return err
	}
	defer f.Close()
	w := csv.NewWriter(f)
	defer w.Flush()
	w.Write([]string{"timestamp", "name", "address", "rssi", "services"})
	for _, d := range s.devices {
		services := ""
		for i, svc := range d.Services {
			if i > 0 {
				services += "; "
			}
			services += svc
		}
		w.Write([]string{d.Timestamp, d.Name, d.Address, fmt.Sprintf("%d", d.RSSI), services})
	}
	return nil
}

func (s *Scanner) ExportTXT(filename string) error {
	f, err := os.Create(filename)
	if err != nil {
		return err
	}
	defer f.Close()
	for _, d := range s.devices {
		svcStr := ""
		for i, svc := range d.Services {
			if i > 0 {
				svcStr += ", "
			}
			svcStr += svc
		}
		line := fmt.Sprintf("%s | %s | %s | RSSI: %d dBm | Services: %s\n",
			d.Timestamp, d.Name, d.Address, d.RSSI, svcStr)
		f.WriteString(line)
	}
	return nil
}

func main() {
	var (
		timeout    int
		filter     string
		exportJSON string
		exportCSV  string
		exportTXT  string
	)
	flag.IntVar(&timeout, "timeout", 5, "Scan duration in seconds")
	flag.StringVar(&filter, "filter", "", "Regex filter for name")
	flag.StringVar(&exportJSON, "export-json", "", "Export to JSON")
	flag.StringVar(&exportCSV, "export-csv", "", "Export to CSV")
	flag.StringVar(&exportTXT, "export-txt", "", "Export to TXT")
	flag.Parse()

	scanner := NewScanner(timeout, filter)
	if err := scanner.Scan(); err != nil {
		fmt.Fprintf(os.Stderr, "Scan error: %v\n", err)
		os.Exit(1)
	}
	scanner.PrintResults()

	if exportJSON != "" {
		if err := scanner.ExportJSON(exportJSON); err != nil {
			fmt.Fprintf(os.Stderr, "Export error: %v\n", err)
		} else {
			fmt.Printf("Exported to %s (JSON)\n", exportJSON)
		}
	}
	if exportCSV != "" {
		if err := scanner.ExportCSV(exportCSV); err != nil {
			fmt.Fprintf(os.Stderr, "Export error: %v\n", err)
		} else {
			fmt.Printf("Exported to %s (CSV)\n", exportCSV)
		}
	}
	if exportTXT != "" {
		if err := scanner.ExportTXT(exportTXT); err != nil {
			fmt.Fprintf(os.Stderr, "Export error: %v\n", err)
		} else {
			fmt.Printf("Exported to %s (TXT)\n", exportTXT)
		}
	}
}
