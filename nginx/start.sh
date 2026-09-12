#!/bin/sh
set -eu

# Utiliza los servidores DNS que Railway entrega al contenedor.
awk '
    $1 == "nameserver" {
        address = $2

        if (index(address, ":") > 0) {
            address = "[" address "]"
        }

        servers = servers " " address
    }

    END {
        if (servers == "") {
            exit 1
        }

        print "resolver" servers " valid=30s;"
    }
' /etc/resolv.conf > /etc/nginx/resolvers.conf

# Comprueba la configuración antes de iniciar.
nginx -t

# Mantiene Nginx como proceso principal del contenedor.
exec nginx -g 'daemon off;'