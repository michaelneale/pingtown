# PINGTOWN

Buildhive status:
[![Build Status](https://buildhive.cloudbees.com/job/michaelneale/job/pingtown/badge/icon)](https://buildhive.cloudbees.com/job/michaelneale/job/pingtown/)

Cover your user visible urls with ping checks. See resource/public/index.html for documentation on how to use it. 
Rough features: configurable timeouts, response code ranges, pattern matching, check expiry, delayed start, failure counting, check authenticated sites, check dependencies, and more.

## Configuration

To run this, you wil need the following environment variables or system properties:
    
    endpoint - endpoint for Pagerduty style api to be notified of DOWN and UP events. Default to pagerduty themselves.
    endpoint_service_key - service key for the above. 
    pingtown_db - the URL of the couchdb service for storing checks (for backup/restart)
    pingtown_db_key - key for couchdb
    pingtown_db_secret - secret for couchdb


## Developing/running locally

	lein ring server


## Deploying
    Set your environment variabls (eg on cloudbees): 	
    bees config:set -a account/app <variable name>=<value>
    then deploy: 
	lein cloudbees deploy
    (if you have credentials setup for cloudbees account)

## License

 Copyright 2012 Michael Neale

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
