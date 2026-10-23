# InteractiveSystem

InteractiveSystem ist ein digitales Lagerverwaltungssystem zur Verwaltung von Lagerbeständen, Ausleihen und Rückgaben. Durch flexible Speicheranbindungen, automatisierte Abläufe und verschiedene Exportmöglichkeiten ermöglicht es eine strukturierte und effiziente Organisation mehrerer Lager innerhalb einer einzigen Anwendung.

## ✨ Funktionen

### 📦 Lagerverwaltung
- **Mehrere Lager:** Verwaltung mehrerer Lager innerhalb einer einzigen App.
- **Individuelle Sortierung:** Sortierung der Gegenstände innerhalb eines Lagers.
- **Packlisten:** Erstellung und Verwaltung von Packlisten zur übersichtlichen Zusammenstellung benötigter Gegenstände.
- **NFC- und QR-Code-System:** Identifikation und Verwaltung von Gegenständen mithilfe von NFC-Tags und QR-Codes.

### 🔄 Ausleih- und Rückgabesystem
- **Automatische Ausleihe:** Das integrierte Packsystem ermöglicht automatisierte Ausleihvorgänge.
- **Eigene Ausleihen und Rückgaben:** Übersicht über persönliche Ausleih- und Rückgabevorgänge.
- **Ausführliches Logsystem:** Nachvollziehbare Protokollierung relevanter Vorgänge im System.
- **Automatisches Ausblenden von Rückgaben:** Abgeschlossene Rückgaben werden nach drei Tagen aus der entsprechenden Übersicht ausgeblendet.

### 💾 Speicherverwaltung
- **Flexible Speicherorte:** Anbindung von Speicherorten über WebDAV, FTP, SFTP und SMB.
- **Neuerstellung:** Möglichkeit, neue Lager beziehungsweise Systemstrukturen anzulegen.
- **Migration:** Übertragung bestehender Daten und Strukturen in eine neue Umgebung.
- **Rollbacksystem:** Wiederherstellung vorheriger Zustände über Backups. Es werden maximal fünf Backups vorgehalten.

### 🔐 Sicherheit und Benutzerverwaltung
- **Administratorpasswort:** Das Passwort muss mindestens acht Zeichen lang sein und folgende Anforderungen erfüllen:
  - Mindestens ein Großbuchstabe
  - Mindestens ein Kleinbuchstabe
  - Mindestens eine Zahl
  - Mindestens ein Sonderzeichen
- **Benutzer-PIN:** Normale Nutzer authentifizieren sich über eine vierstellige PIN.
- **Benutzererstellung:** Erstellung und Verwaltung von Administratoren und normalen Nutzern mit unterschiedlichen Authentifizierungsanforderungen.

### 📄 Exportmöglichkeiten
- **CSV-Export:** Export von Lagerbeständen zur weiteren Verarbeitung in Tabellenkalkulationsprogrammen.
- **PDF-Export:** Export von Lagerbeständen als übersichtliche PDF-Dokumente.
- **Packlisten:** Bereitstellung von Packlisten für Planung und Durchführung.

## 🗂️ Unterstützte Speicherprotokolle

| Protokoll | Beschreibung |
|---|---|
| WebDAV | Zugriff auf kompatible WebDAV-Speicher |
| FTP | Verbindung über das FTP-Protokoll |
| SFTP | Verschlüsselte Dateiübertragung über SSH |
| SMB | Zugriff auf Netzwerkfreigaben |

## 🛠️ Funktionsübersicht

| Bereich | Funktionen |
|---|---|
| Lagerverwaltung | Mehrere Lager, individuelle Sortierung |
| Ausleihe | Automatische Ausleihe, persönliche Ausleihübersicht |
| Rückgabe | Rückgabeübersicht, automatisches Ausblenden nach drei Tagen |
| Protokollierung | Ausführliches Logsystem |
| Sicherheit | Administratorpasswort mit Mindestanforderungen, vierstellige Nutzer-PIN |
| Backups | Rollbacksystem mit maximal fünf Backups |
| Identifikation | NFC- und QR-Code-System |
| Datenexport | CSV- und PDF-Export |
| Datenverwaltung | Neuerstellung und Migration |
| Speicheranbindung | WebDAV, FTP, SFTP und SMB |
| Packlisten | Erstellung und Verwaltung von Packlisten |

## 🎯 Ziel

InteractiveSystem vereint Lagerverwaltung, Ausleihprozesse und Bestandsdokumentation in einer zentralen Anwendung.

Das System ist darauf ausgelegt, wiederkehrende Arbeitsabläufe zu vereinfachen, Gegenstände schnell zu identifizieren und Lagerbestände übersichtlich zu verwalten. Durch flexible Speicheranbindungen, automatisierte Ausleihvorgänge und integrierte Exportmöglichkeiten eignet es sich für unterschiedliche Einsatzbereiche, in denen Gegenstände organisiert, ausgegeben und zurückgenommen werden müssen.

---

*Weitere Informationen zu Installation, Konfiguration und Nutzung werden entsprechend dem Projektumfang ergänzt.*
