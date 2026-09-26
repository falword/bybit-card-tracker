# Privacy / Конфиденциальность

CardTrack keeps Bybit API keys and spend history on this device. There is no analytics SDK, crash reporter, or third-party telemetry.

## English

- **Keys:** Bybit API key and secret are stored with Android Keystore and encrypted on-device storage. They are not uploaded to any CardTrack server (there is none).
- **Spend data:** Transactions live in an encrypted Room / SQLCipher database on the device.
- **Network:** The only network destination is Bybit, used to verify the key and sync card, transfer, and funding history.
- **Wipe:** Settings → Delete key and data removes the key and local history from this device. Also revoke the key in Bybit → API.

## Русский

- **Ключи:** API-ключ и секрет Bybit хранятся через Android Keystore и шифрованное хранилище на устройстве. Они не отправляются на сервер CardTrack (его нет).
- **Операции:** История хранится в зашифрованной базе Room / SQLCipher на устройстве.
- **Сеть:** Единственный сетевой адрес — Bybit: проверка ключа и синхронизация карты, переводов и funding.
- **Удаление:** Настройки → Удалить ключ и данные стирают ключ и локальную историю с этого устройства. Отзови ключ ещё в Bybit → API.
