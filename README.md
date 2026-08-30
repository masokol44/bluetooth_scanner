# Bluetooth Scanner (экспорт)

Многоязычная утилита для сканирования Bluetooth-устройств с возможностью экспорта результатов в различные форматы.  
Поддерживает вывод информации об устройствах (имя, MAC-адрес, RSSI, список сервисов) и сохранение в JSON, CSV или TXT.

## Особенности
- Сканирование ближайших Bluetooth-устройств (классических и BLE).
- Отображение имени, MAC-адреса, уровня сигнала (RSSI) и сервисов.
- Сохранение результатов в файлы форматов: JSON, CSV, TXT.
- Настраиваемое время сканирования (таймаут).
- Фильтрация устройств по имени (регулярное выражение).
- Цветной вывод в терминале (где поддерживается).
- Поддержка аргументов командной строки для автоматизации.

## Установка и запуск
Для каждого языка требуются соответствующие инструменты и зависимости (указаны ниже).

### Запуск на разных языках

1. **Python**  
   Установка: `pip install bleak colorama`  
   Запуск: `python bluetooth_scanner.py --timeout 5 --export-json devices.json`

2. **JavaScript (Node.js)**  
   Установка: `npm install @abandonware/noble commander chalk`  
   Запуск: `node bluetooth_scanner.js --timeout 5 --export-csv devices.csv`

3. **Go**  
   Установка: `go get github.com/go-ble/ble`  
   Запуск: `go run bluetooth_scanner.go --timeout 5 --export-txt devices.txt`

4. **Rust**  
   Добавьте `btleplug`, `serde`, `clap` в `Cargo.toml`.  
   Запуск: `cargo run -- --timeout 5 --export-json devices.json`

5. **Java**  
   Используйте BlueCove (скачайте JAR).  
   Сборка: `javac -cp bluecove.jar BluetoothScanner.java`  
   Запуск: `java -cp .;bluecove.jar BluetoothScanner --timeout 5 --export-csv devices.csv`

6. **C# (.NET Core)**  
   Установка: `dotnet add package InTheHand.Net.Bluetooth`  
   Запуск: `dotnet run -- --timeout 5 --export-json devices.json`

7. **C++ (Linux)**  
   Требуется BlueZ и libbluetooth-dev.  
   Сборка: `g++ -std=c++11 -o bluetooth_scanner bluetooth_scanner.cpp -lbluetooth -ljsoncpp`  
   Запуск: `sudo ./bluetooth_scanner --timeout 5 --export-txt devices.txt`

8. **Kotlin (JVM)**  
   Используйте BlueCove (как в Java).  
   Сборка: `kotlinc -cp bluecove.jar BluetoothScanner.kt`  
   Запуск: `kotlin -cp .;bluecove.jar BluetoothScannerKt --timeout 5 --export-json devices.json`

## Использование

Общие аргументы командной строки (где применимо):

- `--timeout <сек>` – время сканирования (по умолчанию 5).
- `--filter <regex>` – фильтр по имени устройства.
- `--export-json <файл>` – экспорт в JSON.
- `--export-csv <файл>` – экспорт в CSV.
- `--export-txt <файл>` – экспорт в TXT.
- `--help` – справка.

Пример (Python):
```bash
python bluetooth_scanner.py --timeout 10 --filter "MyDevice" --export-json result.json
Структура репозитория
text
/
├── README.md
├── bluetooth_scanner.py
├── bluetooth_scanner.js
├── bluetooth_scanner.go
├── bluetooth_scanner.rs
├── BluetoothScanner.java
├── BluetoothScanner.cs
├── bluetooth_scanner.cpp
└── BluetoothScanner.kt
Лицензия
MIT
