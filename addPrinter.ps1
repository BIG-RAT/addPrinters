param (
    [parameter(Position=0, Mandatory=$false)]
    $dataFile
)

## ensure we're using TLS1.2
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# ignore self signed certs
function Ignore-SelfSignedCerts
{
    try {
    Write-Host "Adding TrustAllCertsPolicy type." -ForegroundColor White
    Add-Type -TypeDefinition  @"
    using System.Net;
    using System.Security.Cryptography.X509Certificates;
    public class TrustAllCertsPolicy : ICertificatePolicy
    {
        public bool CheckValidationResult(
        ServicePoint srvPoint, X509Certificate certificate,
        WebRequest request, int certificateProblem)
        {
            return true;
        }
    }
"@
        Write-Host "TrustAllCertsPolicy type added." -ForegroundColor White
    } catch {
        Write-Host $_ -ForegroundColor "Yellow"
    }
    [System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAllCertsPolicy
}
Ignore-SelfSignedCerts

Clear-Host

## verify a file was provided
if ( ([string]::IsNullOrEmpty($dataFile)) ) {
    Write-Host "`nMissing data file."
    Write-Host "Usage: C:\path\to\appPrinters.ps1 C:\path\to\printers.csv`n"
    exit
}

## verify provided file is accessible
if ( !(Test-Path $dataFile) ) {
    Write-Host "`ncould not locate the data file: $dataFile`n"
    exit
}

## prompmt for credentials
$creds = Get-Credential

## define endpoint used to test authentication
$endpointUrl = "https://your.jamf.server:8443/JSSResource/printers"

## test authentication
try {
    $response = (Invoke-WebRequest -Method GET -Uri $endpointUrl -Credential $creds).statuscode
} catch {
    $response = $_.Exception.Response.StatusCode.Value__
}
if ( $response -eq 401 ) {
    Write-Host "Username and/or password are incorrect."
    exit
}

## set the url so that we can create new objects
$endpointUrl = "$endpointUrl/id/0"

## loop through each printer in the file
foreach($printer in Get-Content $dataFile) {
    $attribArray = $printer.split(",")
    if ( $attribArray[0] -ne "" ) {
        $name = $attribArray[0]
    } else {
        Write-Host "missing printer name: $printer"
        continue
    }
    $category = $attribArray[1]
    if ( $attribArray[2] -ne "" ) {
        $uri = $attribArray[2]
    } else {
        Write-Host "missing printer URI: $printer"
        continue
    }
    if ( $attribArray[3] -ne "" ) {
        $cupsName = $attribArray[3]
    } else {
        Write-Host "missing printer CUPS name: $printer"
        continue
    }
    $model   = $attribArray[4]
    $default = $attribArray[5]
    
    $printerXml = "<?xml version='1.0' encoding='UTF-8'?><printer><name>${name}</name><category>${category}</category><uri>${uri}</uri><CUPS_name>${cupsName}</CUPS_name><model>${model}</model><make_default>${default}</make_default></printer>"
    
    ## try to add the printer
    try {
        $response = Invoke-RestMethod -Method POST -Uri $endpointUrl -Credential $creds -Body $printerXml -ContentType 'application/xml'
        Write-Host "added printer: $name"
    } catch {
        ## provide some error information with $_
        Write-Host "`n***** error adding printer: $name`n$_`n"
    }
}
