#!/bin/bash

if [[ $1 = "" ]];then
    echo "Missing data file."
    echo "Usage: /path/to/createPrinters.sh /path/to/printers.csv"
    exit
fi

if [[ ! -f $1 ]];then
    echo "unable to locate data file: $1"
    exit
fi

echo -n "Jamf Server Admin: "
read uname

echo -n "Password for ${uname}: "
stty -echo
read passwrd
stty echo
echo

## url used to test authentication
endpointUrl="https://jamf.ccs-indy.com:8443/JSSResource/printers"

## test authentication
result=$(curl -w " %{http_code}" -m 10 -sku "${uname}":"${passwrd}" ${endpointUrl} -X GET -H "Accept: application/xml")
statusCode=$(echo $result | awk '{print $NF}')
# echo "statusCode:$statusCode:"
if [[ $statusCode = "401" ]];then
    echo "Incorrect username and/or password."
    exit 0
fi

## set the url so that we can create new objects
endpointUrl="$endpointUrl/id/0"

while read printer;do
    name=$(echo $printer | awk -F',' '{ print $1 }')
    #    echo "name:$name:"
    #    echo "ascii start:"
    #    echo "$name" | tr -d "\n" | od -An -t uC
    #    echo "ascii end:"

    if [[ -z $name ]];then
        echo "missing printer name: ${printer}"
        echo
        continue
    fi
    category=$(echo $printer | awk -F',' '{ print $2 }')
    uri=$(echo $printer | awk -F',' '{ print $3 }')
    if [[ -z ${uri} ]];then
        echo "missing printer URI: ${printer}"
        echo
        continue
    fi
    cups_name=$(echo $printer | awk -F',' '{ print $4 }')
    if [[ -z ${cups_name} ]];then
        echo "missing CUPS name (queue name): ${printer}"
        echo
        continue
    fi
    model=$(echo ${printer} | awk -F',' '{ print $5 }')
    default=$(echo ${printer} | awk -F',' '{ print $6 }')
    printerXml="<?xml version='1.0' encoding='UTF-8'?>\
<printer>\
<name>${name}</name>\
<category>${category}</category>\
<uri>${uri}</uri>\
<CUPS_name>${cups_name}</CUPS_name>\
<model>${model}</model>\
<make_default>${default}</make_default>\
</printer>"

    result=$(curl -sku "${uname}":"${passwrd}" ${endpointUrl} -X POST -H "Content-Type: application/xml" -d "${printerXml}")

    if [[ $(echo $result | grep 'technical details') = "" ]];then
        echo "created printer: ${name}"
    else
        echo "***** failed to create printer: ${name}"
        echo "$result"
        echo
    fi
done << EOL
    $(cat "${1}")
EOL
