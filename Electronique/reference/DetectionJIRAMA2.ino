#include <SPI.h>
#include <EthernetENC.h>
#include <ArduinoHttpClient.h>
#include <ArduinoJson.h>

// --- Broches ---
constexpr int PIN_CAPTEUR_SOURCE1 = 32;   // collecteur du PC817 (INPUT_PULLUP)
constexpr int PIN_RELAIS          = 27;
constexpr int PIN_ENC_CS          = 5;    // chip select de l'ENC28J60 (VSPI)

// --- Niveaux logiques ---
constexpr int  NIVEAU_LIGNE_FIXE_ACTIF   = LOW;   // PC817 conducteur -> secteur présent
constexpr int  RELAIS_SOURCE_2     = HIGH;  // relais collé (NO)  -> Source 2
constexpr int  RELAIS_SOURCE_1     = LOW;   // relais au repos (NC) -> Source 1

// --- Temporisations ---
constexpr unsigned long MS_PAR_SECONDE        = 1000UL;
constexpr unsigned long MS_PAR_MINUTE         = 60UL * MS_PAR_SECONDE;
constexpr unsigned long DELAI_BASCULE_TEST_MS = 5UL * MS_PAR_SECONDE;
constexpr unsigned long DELAI_BASCULE_PROD_MS = 45UL * MS_PAR_MINUTE;

constexpr bool          MODE_TEST             = true;   // false en production

constexpr unsigned long DELAI_BASCULE_MS = MODE_TEST ? DELAI_BASCULE_TEST_MS : DELAI_BASCULE_PROD_MS;

// --- Détection ligneFixe 50Hz par l'octocoupleur ---
constexpr unsigned long FENETRE_DETECTION_US = 25000UL;  // un peu plus d'une période (20 ms)
constexpr unsigned int  PAS_ECHANTILLON_US   = 300;      // pas d'échantillonnage
constexpr unsigned long DELAI_CONFIRMATION_MS = 200;

// --- UART ---
constexpr unsigned long BAUDS_SERIE = 115200;

// --- Routes ---
constexpr const char* ROUTE_HEURE = "/api/esp32/time";
constexpr const char* ROUTE_LIGNE_FIXE = "/api/esp32/%s/lignefixe";

// --- Réseau ---
byte MAC_ESP32[] = {0x02, 0xAB, 0xCD, 0x00, 0x00, 0x0A};

const IPAddress IP_ESP32(192, 168, 5, 20);
const IPAddress IP_PASSERELLE(192, 168, 5, 1);
const IPAddress IP_DNS(192, 168, 5, 1);
const IPAddress MASQUE(255, 255, 255, 0);
const IPAddress IP_SERVEUR(192, 168, 5, 10);

constexpr uint16_t PORT_SERVEUR = 8081;
const char* ID_APPAREIL = "esp3201";
constexpr const char* CLE_API = "2383928049090209";

EthernetClient eth;
HttpClient http(eth, "192.168.5.10", PORT_SERVEUR);

// --- Codes HTTP attendus ---
constexpr int HTTP_OK      = 200;
constexpr int HTTP_CREATED = 201;
constexpr int HTTP_BAD_REQUEST = 400;

// --- Délais réseau ---
constexpr uint32_t         TIMEOUT_REPONSE_HTTP_MS = 2000;
constexpr unsigned long    INTERVALLE_RENVOI_MS    = 5UL * MS_PAR_SECONDE;
constexpr unsigned long    INTERVALLE_RESYNC_MS = 60UL * MS_PAR_MINUTE;

// --- JSON / formats ---
constexpr size_t TAILLE_JSON_HEURE      = 64;
constexpr size_t TAILLE_JSON_LIGNEFIXE  = 128;
constexpr size_t TAILLE_BUFFER_ISO      = 25;   // "YYYY-MM-DDTHH:MM:SSZ" + '\0'
constexpr size_t TAILLE_BUFFER_ROUTE    = 64;
constexpr const char* FORMAT_ISO_UTC    = "%Y-%m-%dT%H:%M:%SZ";

// --- File d'événements ---
constexpr uint8_t CAPACITE_FILE = 20;
constexpr time_t  EPOCH_INCONNU = 0;   // heure non synchronisée

// --- Heure (synchronisée sur le serveur) ---
time_t baseEpoch = EPOCH_INCONNU;
unsigned long baseMillis = 0;

time_t maintenant() { return baseEpoch != EPOCH_INCONNU ? baseEpoch + (millis() - baseMillis) / MS_PAR_SECONDE : EPOCH_INCONNU; }

void resynchroniser() {
  static unsigned long dernier = 0;
  if (millis() - dernier < INTERVALLE_RESYNC_MS) return;
  dernier = millis();
  syncHeure();
}

bool syncHeure() {
  http.get(ROUTE_HEURE);
  int code = http.responseStatusCode();
  String rep = http.responseBody();
  http.stop();
  Serial.print("GET time -> "); Serial.print(code); Serial.print(" "); Serial.println(rep); // DEBUG
  if (code != HTTP_OK) return false;
  JsonDocument doc;
  if (deserializeJson(doc, rep)) return false;
  time_t e = doc["epoch"].as<time_t>();
  if (e <= EPOCH_INCONNU) return false;        // champ absent ou nul -> PAS synchro
  baseEpoch = e;
  baseMillis = millis();
  return true;
}

// --- File d'evenements (conservés tant que le serveur est injoignable) ---
struct Evt { bool present; time_t epoch; };
Evt file[CAPACITE_FILE];
uint8_t nbEvt = 0;

void empiler(bool present) {
  if (nbEvt == CAPACITE_FILE) {                       // pleine : on jette le plus ancien
    memmove(&file[0], &file[1], (CAPACITE_FILE - 1) * sizeof(Evt));
    nbEvt = CAPACITE_FILE - 1;
  }
  file[nbEvt++] = { present, maintenant() };
}

int envoyer(bool present, time_t epoch) {
  JsonDocument doc;                       
  doc["present"] = present;
  if (epoch != EPOCH_INCONNU) {
    char buf[TAILLE_BUFFER_ISO];
    struct tm tm;
    time_t t = epoch;
    gmtime_r(&t, &tm);
    strftime(buf, sizeof(buf), FORMAT_ISO_UTC, &tm);
    doc["capturedAt"] = buf;
  }
  String body;
  serializeJson(doc, body);

  char route[TAILLE_BUFFER_ROUTE];
  snprintf(route, sizeof(route), ROUTE_LIGNE_FIXE, ID_APPAREIL);

  http.beginRequest();
  int err = http.post(route);
  if (err != HTTP_SUCCESS) {          // -1 = connexion impossible
    Serial.print("Connexion echouee, code "); Serial.println(err);
    http.stop();
    return err;
  }
  http.sendHeader("Content-Type", "application/json");
  http.sendHeader("Content-Length", body.length());
  // http.sendHeader("X-API-Key", CLE_API);   // seulement si vous avez activé la clé côté serveur
  http.beginBody();
  http.print(body);
  http.endRequest();

  int code = http.responseStatusCode();
  String rep = http.responseBody();   // ne plus jeter
  http.stop();
  Serial.print("POST lignefixe -> "); Serial.println(code);
  Serial.println(rep);
  return code;
}

void viderFile() {
  static unsigned long dernierEssai = 0;
  if (nbEvt == 0 || millis() - dernierEssai < INTERVALLE_RENVOI_MS) return;
  dernierEssai = millis();

  if (baseEpoch == EPOCH_INCONNU) syncHeure();
  int code = envoyer(file[0].present, file[0].epoch);
  //Serial.print("POST lignefixe -> "); Serial.println(code);

  if (code == HTTP_CREATED || code == HTTP_OK) {
    memmove(&file[0], &file[1], (nbEvt - 1) * sizeof(Evt));
    nbEvt--;
  }
}

// --- Détection secteur ---
bool secteurEnMarche() {
  unsigned long debut = micros();
  while (micros() - debut < FENETRE_DETECTION_US) {
    if (digitalRead(PIN_CAPTEUR_SOURCE1) == NIVEAU_LIGNE_FIXE_ACTIF) return true;
    delayMicroseconds(PAS_ECHANTILLON_US);
  }
  return false;
}

bool secteurFiltre() {
  static bool initialise = false;
  static bool etatStable = false;
  static unsigned long debutChangement = 0;

  bool brut = secteurEnMarche();
  if (!initialise) { etatStable = brut; initialise = true; return etatStable; }

  if (brut == etatStable) { debutChangement = 0; return etatStable; }
  if (debutChangement == 0) debutChangement = millis();
  if (millis() - debutChangement >= DELAI_CONFIRMATION_MS) {
    etatStable = brut;
    debutChangement = 0;
  }
  return etatStable;
}

// --- Logique relais ---
bool enPanne = false;
bool basculeFaite = false;
bool premierPassage = true;
bool etatPrecedent = false;
unsigned long debutCoupure = 0;

void setup() {
  Serial.begin(BAUDS_SERIE);
  pinMode(PIN_CAPTEUR_SOURCE1, INPUT_PULLUP);
  pinMode(PIN_RELAIS, OUTPUT);
  digitalWrite(PIN_RELAIS, RELAIS_SOURCE_1);

  Ethernet.init(PIN_ENC_CS);
  Ethernet.begin(MAC_ESP32, IP_ESP32, IP_DNS, IP_PASSERELLE, MASQUE);
  delay(1000);   // laisse l'ENC28J60 s'initialiser
  if (Ethernet.hardwareStatus() == EthernetNoHardware) Serial.println("ENC28J60 non detecte");
  if (Ethernet.linkStatus() == LinkOFF) Serial.println("Cable Ethernet debranche");

  Serial.print("Materiel: "); Serial.println(Ethernet.hardwareStatus());   // 0 = aucun module
  Serial.print("Lien: ");     Serial.println(Ethernet.linkStatus());       // 1 = ON, 2 = OFF
  Serial.print("IP: ");       Serial.println(Ethernet.localIP());

  if (eth.connect(IP_SERVEUR, PORT_SERVEUR)) {
    Serial.println("TCP vers le serveur: OK");
    eth.stop();
  } else {
  Serial.println("TCP vers le serveur: ECHEC");
}

  http.setHttpResponseTimeout(TIMEOUT_REPONSE_HTTP_MS);
  for (int i = 0; i < 3 && !syncHeure(); i++) delay(500);
}

void loop() {
  //bool secteurActif = secteurEnMarche();
  bool secteurActif = secteurFiltre();

  if (premierPassage || secteurActif != etatPrecedent) {
    empiler(secteurActif);
    etatPrecedent = secteurActif;
    premierPassage = false;
  }

  // CAS 1 : début de coupure
  if (!secteurActif && !enPanne) {
    enPanne = true;
    debutCoupure = millis();
    Serial.println("Coupure detectee, onduleur sur batterie. Chrono demarre.");
  }
  // CAS 2 : coupure trop longue -> Source 2 (une seule fois)
  if (enPanne && !basculeFaite && millis() - debutCoupure >= DELAI_BASCULE_MS) {
    digitalWrite(PIN_RELAIS, RELAIS_SOURCE_2);
    basculeFaite = true;
    Serial.println("Delai ecoule -> bascule sur SOURCE 2 pour recharger l'onduleur.");
  }
  // CAS 3 : retour du secteur
  if (secteurActif && enPanne) {
    enPanne = false;
    basculeFaite = false;
    digitalWrite(PIN_RELAIS, RELAIS_SOURCE_1);
    Serial.println("Source 1 de retour -> retour sur le reseau principal.");
  }

  viderFile();
  resynchroniser();
}