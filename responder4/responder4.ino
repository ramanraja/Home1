// Internet switch to switch on 4 different lights.
// Receives an MQTT message to switch ON/ OFF
// Sends a binary string with status of the 4 relays.
// TODO: automaticlly switch on the relay if the ambient light level is low; fix an LDR for this.

#include "common.h" 
#include "config.h"
#include "myfi.h"
#include "MqttLite.h"
#include "otaHelper.h"
#include <Timer.h>   // https://github.com/JChristensen/Timer
int led = D4;  // 2;  // GPIO 2
int relays[4] = {D2,D5,D6,D7};  // {4,14,12,13}; 
boolean stats[4] = {0,0,0,0};
char text[128];
    
Timer T;
Config C;
MqttLite M;
OtaHelper O;

void setup() {
    init_serial();
    init_hardware();
    C.init();
    C.dump();   
    M.init (&C);
    O.init (&C, &M);
    sprintf (text, "-- MQTT responder V %d is restaring...", FIRMWARE_VERSION);
    M.publish(text);
    delay(250);
    send_status();
}

void loop() {
    T.update(); 
    M.update();    
}

void init_hardware() {
    pinMode(led, OUTPUT);   
    for (int i=0; i<4; i++) {   // TODO: remember last status after power failure
      pinMode(relays[i], OUTPUT);  
      digitalWrite (relays[i], HIGH);  // active low
      for (int i=0; i<4; i++)
          stats[i] = 0;  // 0 means relay status is OFF
    }
    blinker();
}

void init_serial () {
    #ifdef ENABLE_DEBUG
        Serial.begin(BAUD_RATE); 
        #ifdef VERBOSE_MODE
          Serial.setDebugOutput(true);
        #endif
        Serial.setTimeout(250);
    #endif    
    SERIAL_PRINTLN("\n*******  MQTT Responder starting... ********\n"); 
    SERIAL_PRINT("FW version: ");
    SERIAL_PRINTLN(FIRMWARE_VERSION);
}

void blinker() {
    for (int i=0; i<6; i++) {
        digitalWrite(led, LOW);
        delay(100);
        digitalWrite(led, HIGH);
        delay(100);        
    }
}

void check_for_updates() {
    SERIAL_PRINTLN ("\n<<<<<<---------  checking for FW updates... ----------->>>>>>\n");
    int result = O.check_and_update();  // if there was an update, this will restart 8266
    SERIAL_PRINT ("OtaHelper: response code: ");   // if the update failed
    SERIAL_PRINTLN (result);
    T.oscillate(led, 500, HIGH, 4);  // ready to receive commands again
}

//-----------------------------------------------------------------------------------

int relay_number = 0;
void  app_callback(const char* command_string) {
    SERIAL_PRINTLN ("app_callback: MQTT message received :");
    SERIAL_PRINTLN (command_string);
    if (strlen (command_string) < 3) {
        SERIAL_PRINTLN ("Command has to be either STA or UPD or ONx or OFFx where x is from 0-9");
        return;
    }
    // STATUS command
    if (command_string[0]=='S' && command_string[1]=='T' && command_string[2]=='A') {
        send_status();
        return;
    }
    // UPDATE firmware by OTA
    // Run a HTTP server at the specified URL and serve the firmware file
    if (command_string[0]=='U' && command_string[1]=='P' && command_string[2]=='D') {
        check_for_updates();  // this may reset the chip after update
        return;
    }    
    // ON commands
    if (command_string[0]=='O' && command_string[1]=='N') {
        if (command_string[2] < '0' ||  command_string[2] > '9') {
            SERIAL_PRINTLN ("-- Error: Relay # has to be between ASCII '0' and '9' --");
            return;
        }
        relay_number = command_string[2] - '0';
        SERIAL_PRINT ("Remote command: ON; Relay : ");
        SERIAL_PRINTLN (relay_number);
        stats[relay_number] = 1;  // 1 means the realy is ON
        digitalWrite (relays[relay_number], LOW);  // active low
        //M.publish("The relay is ON");
        send_status();
        T.oscillate(led,50,HIGH,8);        
        return;
    }
    // OFF commands
    if (strlen (command_string) < 4) {
        SERIAL_PRINTLN ("Command has to be either ONx or OFFx where x is from 0-9");
        return;
    }    
    if (command_string[0]=='O' && command_string[1]=='F' && 
        command_string[2]=='F') {
        if (command_string[3] < '0' ||  command_string[3] > '9') {
            SERIAL_PRINTLN ("-- Error: Relay # has to be between ASCII '0' and '9' --");
            return;
        }
        relay_number = command_string[3] - '0';
        SERIAL_PRINT ("Remote command: ON; Relay : ");
        SERIAL_PRINTLN (relay_number);
        stats[relay_number] = 0;  // 0 means the realy is OFF
        digitalWrite (relays[relay_number], HIGH);  // active low
        //M.publish("The relay is switched OFF");
        send_status();
        T.oscillate(led,200,HIGH,4);        
        }
    return;   
    /***
    // EXIT command: may be useful to disable the app, in case of a hacker attack ?
    if (command_string[0]=='E' && command_string[1]=='X' && 
        command_string[2]=='I' && command_string[3]=='T') {
        SERIAL_PRINTLN ("Remote cam app has terminated !");
        if (C.sleep_deep) {
            M.publish("IoT relay goes to sleep..");
            digitalWrite (led, LOW);
            delay(2000);
            digitalWrite (led, HIGH);
            ESP.deepSleep(0);
        }
        else
            T.pulse(led,2000,HIGH);
    }   
    TODO: disconnect from MQTT server so that LWT is invoked
    ***/  
}

char status_str [5];
void send_status() {
    sprintf (status_str, "%1d%1d%1d%1d", stats[0],stats[1],stats[2],stats[3]);
    M.publish(status_str);
}
