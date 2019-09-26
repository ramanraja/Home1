// Reads a PIR and triggers a relay when any movement is detected.
// Measures light level with an LDR and posts it every 10 minutes to ThingSpeak.
// Automatically switches on the light when the PIR is triggered - when light is below a threshold.
// OTA firmware updates enabled

#include "common.h" 
#include "config.h"
#include "myfi.h"
#include "httpPoster.h"
#include "otaHelper.h"
#include <Timer.h>   // https://github.com/JChristensen/Timer

// empirically found for the given LDR with ESP12 (not NodeMCU):
// complete darkness => LDR voltage is maximum ( > 1 V); analog reading is 1024
// very bright => LDR voltage drops to less than 0.5V; analog reading drops to 100 or less

// Two thresholds with hysteresis
#define DAY_THRESHOLD     450
#define NIGHT_THRESHOLD   650

int led = 2;   // D4;  // GPIO 2
int analog_pin = A0;
int relay = 12;  
int pir = 13;
   
Timer T;
Config C;
MyFi W;
HttpPoster H; 
OtaHelper O;

void setup() {
    init_serial();
    init_hardware();     
    C.init();
    C.dump(); 
    decide_day_or_night(); // find out the initial condition
    W.init (&C);
    O.init (&C);
    H.init(&C, &W);
    send_to_server (-1, -1); // restart indicator
    //relay_on();
    check_for_updates(); 
    //T.after (30000, relay_off); // in case power fails and restores
    T.every (100, check_pir);
    T.every (60000, check_ldr);     
}

void loop() {
    T.update();    
}

bool night_time = true;  
bool occupied = false;
bool temp_status = 0;
int status = 0;  // mostly occupancy status; Occasional negative values indicate restart, FW update failure etc
    
void check_pir() {
    temp_status = digitalRead(pir);
    digitalWrite(led, !temp_status);  // active low LED
    if (!temp_status)  // no trigger detected 
        return;
    // remember that PIR was triggered in this epoch
    status = 1;  // latch the value to send it to server on the next occasion
    if (!occupied) {
        ////occupied = true; // subtle bug! do not put this here.
        if (night_time) {
            relay_on();    
            T.after (5*60000, relay_off); // this will clear the occupied flag  // 5*60000
        }
    }
}

int temp_light_level = 0;
int light_level = 0;

void decide_day_or_night() {
    temp_light_level = 0;
    for (int i=0; i<10; i++) {
        temp_light_level += analogRead(analog_pin);
        delay(100);
    }
    light_level = (int)(temp_light_level / 10); 
    temp_light_level = 0;    
    if (light_level < DAY_THRESHOLD)
        night_time = false;
    else
        night_time = true;
}

int count = 0;   
int result = -10;

void check_ldr () { // this is called once every minute
      temp_light_level += analogRead(analog_pin);
      count++;  
      if (count == 10) {  // send once in 10 minutes
          light_level = (int)(temp_light_level / count);     
          count = 0; 
          temp_light_level = 0;   // prepare for next round of accumulation
          SERIAL_PRINT ("Light level: ");
          SERIAL_PRINTLN (light_level);
          if (light_level > NIGHT_THRESHOLD)
              night_time = true;  
          else
              if (light_level < DAY_THRESHOLD)
                  night_time = false;
          // if in between the two thresholds, no change in label
          send_to_server (light_level, status);
          status = 0;  // reset occupancy status for the next epoch
      }
}

char server_url[MAX_STRING_LENGTH];
void send_to_server (int value1, int value2) {
      sprintf (server_url, "%s&field1=%d&field2=%d", C.data_prod_url, value1, value2);
      SERIAL_PRINTLN (server_url);
      result = H.get(server_url);
      SERIAL_PRINT ("HTTP GET result: ");
      SERIAL_PRINTLN (result);
}

void init_hardware() {
    pinMode(relay, OUTPUT);
    digitalWrite (relay, LOW);  // active high      
    pinMode(led, OUTPUT);     
    pinMode(pir, INPUT);  
    blinker();    
}

void relay_on() {
    digitalWrite (relay, HIGH);  // active high    
    SERIAL_PRINTLN ("Relay is ON");
    occupied = true; // this flag must be set here; not in check_pir
                    // otherwise it will never be cleared when day-night transition occurs
}

void relay_off() {
    digitalWrite (relay, LOW);  // active high    
    SERIAL_PRINTLN ("Relay is OFF");
    occupied = false;  // prepare it for next trigger
}

void init_serial () {
    #ifdef ENABLE_DEBUG
        Serial.begin(BAUD_RATE); 
        #ifdef VERBOSE_MODE
          Serial.setDebugOutput(true);
        #endif
        Serial.setTimeout(250);
    #endif    
    SERIAL_PRINTLN("\n*******  Ligths controller starting... ********\n"); 
    SERIAL_PRINT("FW version: ");
    SERIAL_PRINTLN(FIRMWARE_VERSION);
}

void blinker() {
    for (int i=0; i<6; i++) {
        digitalWrite(led, LOW); // active low
        delay(100);
        digitalWrite(led, HIGH);
        delay(100);        
    }
}

void blinker2() {
    for (int i=0; i<3; i++) {
        digitalWrite(led, LOW); // active low
        delay(400);
        digitalWrite(led, HIGH);
        delay(400);        
    }
}

int fw_result_code = 0;
void check_for_updates() {
    SERIAL_PRINTLN ("\n<<<<<<---------  checking for FW updates... ----------->>>>>>\n");
    fw_result_code = O.check_and_update();  // if there was an update, this will restart 8266
    SERIAL_PRINT ("OtaHelper: response code: ");   // will reach here only if the update failed
    SERIAL_PRINTLN (fw_result_code);
    //T.oscillate(led, 500, HIGH, 4);  // update check finished
    blinker2(); // a bit of a delay 
    T.after (20000, send_fw_update_result); // ThingSpeak needs min 15 sec between messages
}

void send_fw_update_result() {
  send_to_server (-2, fw_result_code);
}
