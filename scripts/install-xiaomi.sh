#!/usr/bin/env bash
# Install the bundled APK on Xiaomi TV Box S (Google TV / Android TV 14) over LAN
# without uninstalling the existing application. Same APK and package as the Fire Stick bundle.
set -euo pipefail
export LC_ALL=C
fail() { printf '\nBłąd: %s\n' "$*" >&2; exit 1; }
package_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
apk="$package_dir/OKTV-Player-TV.apk"
for command in adb sha256sum timeout awk; do
  command -v "$command" >/dev/null || fail "Brak $command. W terminalu Linux wykonaj: sudo apt update && sudo apt install -y adb coreutils"
done
[[ -f "$apk" && -f "$package_dir/SHA256SUMS" ]] || fail 'Rozpakuj całą paczkę ZIP. Brakuje APK lub SHA256SUMS obok instalatora.'
# Require one exact filename: do not allow a checksum file to select arbitrary files.
expected=$(awk '$2=="OKTV-Player-TV.apk" && length($1)==64 {print $1}' "$package_dir/SHA256SUMS")
[[ "$expected" =~ ^[[:xdigit:]]{64}$ ]] || fail 'Niepoprawny plik SHA256SUMS. Pobierz paczkę ponownie.'
actual=$(sha256sum -- "$apk"); actual=${actual%% *}
[[ "$actual" == "$expected" ]] || fail 'Suma kontrolna APK się nie zgadza. Pobierz i rozpakuj paczkę ponownie.'
usage='Użycie: bash install-xiaomi.sh [adres-IP[:port]] [--pair adres-IP:port-parowania]'
address=''; pair=''
while (($#)); do
  case "$1" in
    --pair) (($#>=2)) || fail "$usage"; pair=$2; shift 2 ;;
    *) [[ -z "$address" ]] || fail "$usage"; address=$1; shift ;;
  esac
done
ipv4_re='^([0-9]{1,3}\.){3}[0-9]{1,3}(:[0-9]{1,5})?$'
valid_ipv4() {
  local value=$1 host port=1 a b c d octet
  [[ "$value" =~ $ipv4_re ]] || return 1
  host=${value%%:*}
  [[ "$value" != *:* ]] || port=${value##*:}
  IFS=. read -r a b c d <<< "$host"
  for octet in "$a" "$b" "$c" "$d"; do ((10#$octet<=255)) || return 1; done
  ((10#$port>=1 && 10#$port<=65535))
}
if [[ -n "$pair" ]]; then
  [[ "$pair" == *:* ]] && valid_ipv4 "$pair" || fail 'Adres parowania podaj jako IP:port z ekranu „Sparuj urządzenie za pomocą kodu”, np. 192.168.1.50:37123.'
fi
if [[ -z "$address" ]]; then
  printf 'Adres IP Xiaomi TV Box (Ustawienia → Sieć i internet → Twoja sieć): '
  read -r address || fail 'Nie podano adresu IP.'
fi
[[ "$address" =~ $ipv4_re ]] || fail 'Podaj IPv4, np. 192.168.1.50 lub 192.168.1.50:5555.'
host=${address%%:*}; port=5555
[[ "$address" != *:* ]] || port=${address##*:}
IFS=. read -r a b c d <<< "$host"
for octet in "$a" "$b" "$c" "$d"; do ((10#$octet<=255)) || fail 'Niepoprawny adres IPv4.'; done
((10#$port>=1 && 10#$port<=65535)) || fail 'Niepoprawny port.'
target="$((10#$a)).$((10#$b)).$((10#$c)).$((10#$d)):$((10#$port))"
auth_wait=${OKTV_AUTH_TIMEOUT:-90}
[[ "$auth_wait" =~ ^[0-9]{1,3}$ ]] && ((10#$auth_wait>=1 && 10#$auth_wait<=300)) || fail 'OKTV_AUTH_TIMEOUT musi wynosić od 1 do 300 sekund.'
auth_wait=$((10#$auth_wait))
if [[ -n "$pair" ]]; then
  printf '\nParowanie z %s. Wpisz sześciocyfrowy kod widoczny na ekranie TV.\n' "$pair"
  timeout 120 adb pair "$pair" || fail 'Parowanie nie powiodło się. Otwórz ponownie „Sparuj urządzenie za pomocą kodu” i użyj nowego portu oraz kodu.'
fi
printf '\nŁączę z %s. Włącz Debugowanie USB w Opcjach programisty Xiaomi TV Box.\n' "$target"
connect_output=$(timeout 15 adb connect "$target" 2>&1) || true
printf '%s\n' "$connect_output"
state() { timeout 8 adb devices 2>/dev/null | awk -v target="$target" '$1==target {print $2; exit}'; }
deadline=$((SECONDS+auth_wait)); device_state=$(state) || device_state=''
if [[ "$device_state" != device ]]; then
  printf 'Na telewizorze zaznacz „Zawsze zezwalaj z tego komputera” i zaakceptuj „Zezwolić na debugowanie USB?”.\n'
fi
while [[ "$device_state" == unauthorized || "$device_state" == offline ]]; do
  ((SECONDS<deadline)) || break
  sleep 2
  device_state=$(state) || device_state=''
done
[[ "$device_state" == device ]] || fail 'Brak połączenia ADB. Sprawdź IP, wspólną sieć Wi-Fi/LAN, Debugowanie USB i zgodę na ekranie TV. Jeśli port 5555 jest zamknięty, włącz Debugowanie bezprzewodowe i użyj --pair (instrukcja w CZYTAJ-XIAOMI.md). Sieć gościnna może blokować połączenia między urządzeniami.'
sdk=$(timeout 15 adb -s "$target" shell getprop ro.build.version.sdk | tr -d '\r\n') || fail 'Nie można odczytać wersji systemu.'
[[ "$sdk" =~ ^[0-9]{1,3}$ ]] || fail 'Urządzenie nie udostępnia zgodnego systemu Android TV.'
((10#$sdk>=25)) || fail 'Wymagany Android TV 7.1+. Ta wersja systemu jest zbyt stara.'
model=$(timeout 15 adb -s "$target" shell getprop ro.product.model | tr -d '\r\n') || model='Android TV'
case "$model" in
  *MiTV*|*MIBOX*|*Xiaomi*|*xiaomi*) ;;
  *) printf '\nUwaga: model „%s” nie wygląda na Xiaomi TV Box. Instaluję ten sam APK Android TV.\n' "$model" ;;
esac
printf '\nUrządzenie: %s (Android API %s). Instaluję aktualizację z zachowaniem danych…\n' "$model" "$sdk"
remote_apk="/data/local/tmp/oktv-player-$(date +%s)-$$.apk"
cleanup() { timeout 10 adb -s "$target" shell rm -f "$remote_apk" >/dev/null 2>&1 || true; }
trap cleanup EXIT
timeout 180 adb -s "$target" push "$apk" "$remote_apk" || fail 'Nie udało się przesłać APK. Sprawdź połączenie i wolne miejsce.'
install_status=0
install_output=$(timeout 300 adb -s "$target" shell pm install -r "$remote_apk" 2>&1) || install_status=$?
printf '%s\n' "$install_output"
if ((install_status==124 || install_status==137)); then
  fail 'Instalacja nie została potwierdzona w limicie czasu. Sprawdź ekran TV i połączenie. System może jeszcze kończyć instalację.'
fi
if ((install_status!=0)) || ! printf '%s\n' "$install_output" | tr -d '\r' | awk '$0=="Success" {ok=1} END {exit !ok}'; then
  case "$install_output" in
    *INSTALL_FAILED_UPDATE_INCOMPATIBLE*) fail 'Zainstalowana aplikacja ma inny podpis. Nie usunięto jej ani danych. Zrób kopię ustawień przed ewentualnym ręcznym odinstalowaniem starej wersji.' ;;
    *INSTALL_FAILED_VERSION_DOWNGRADE*) fail 'Na Xiaomi TV Box jest nowsza wersja aplikacji. Nie wymuszono obniżenia wersji.' ;;
    *INSTALL_FAILED_INSUFFICIENT_STORAGE*) fail 'Brakuje miejsca na Xiaomi TV Box. Zwolnij miejsce i spróbuj ponownie.' ;;
    *) fail 'System odrzucił instalację. Szczegóły znajdują się powyżej. Nie usunięto istniejącej aplikacji.' ;;
  esac
fi
installed=$(timeout 15 adb -s "$target" shell pm path pl.tvplayer.television 2>&1) || fail 'Nie można potwierdzić obecności aplikacji.'
[[ "$installed" == package:* ]] || fail 'System nie potwierdził obecności pakietu pl.tvplayer.television.'
printf '\nOKTV Player został zainstalowany.\n'
launch=$(timeout 30 adb -s "$target" shell am start -W -n pl.tvplayer.television/pl.tvplayer.TvActivity 2>&1) || launch='Error: uruchomienie przekroczyło czas lub nie powiodło się'
if [[ "$launch" == *'Error:'* || "$launch" == *'Exception'* ]]; then
  printf 'Otwórz OKTV Player ręcznie: Aplikacje → Twoje aplikacje.\n%s\n' "$launch"
else
  printf 'Aplikacja jest otwarta. Wybierz „Zmień źródło” → „Dodaj przez telefon”.\n'
fi
printf 'Po zakończeniu możesz wyłączyć Debugowanie USB w Opcjach programisty Xiaomi TV Box.\n'
