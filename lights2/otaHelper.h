// otaHelper.h
#ifndef OTAHELPER_H
#define OTAHELPER_H

#include "common.h"
#include "config.h"
//#include "mqttLite.h"
#include <ESP8266HTTPClient.h>
#include <ESP8266httpUpdate.h>


class Dummy {
public :
    inline void publish(char *str) {
        SERIAL_PRINTLN ("-------- OTA helper: Place holder violation ! ----------");
    }    
}; 

class OtaHelper {
 public:
    OtaHelper();
    void init(Config *configptr);
    //void init(Config* configptr, MqttLite* mqttptr);  
    int check_and_update();
    bool check_version();    
    int update_firmware();  
 private:
     Config *pC;
     //MqttLite *pM;
     Dummy *pM;
};


#endif 
