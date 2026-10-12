const PERIODO = 'PON_AQUI_EL_PERIODO'   // texto exacto como aparece en el selector
const PROGRAMA = 'PON_AQUI_EL_PROGRAMA' // texto exacto como aparece en el selector
const TABLA = 'table.oferta-tabla tbody tr'

describe('HU-010 Consultar cursos', () => {
    beforeEach(() => {
        cy.loginComo(Cypress.env('ESTUDIANTE_CEDULA'), Cypress.env('ESTUDIANTE_CLAVE'))
        cy.visit('/estudiante/cursos-disponibles')
        cy.contains('h1', 'Cursos disponibles').should('be.visible')
        cy.get('#idPeriodo option').should('have.length.greaterThan', 1)
    })

    const buscar = () => {
        cy.get('#idPeriodo').select(PERIODO)
        cy.get('#idPrograma').select(PROGRAMA)
        cy.contains('button', 'Buscar cursos').click()
        cy.get(TABLA).should('have.length.greaterThan', 0)
    }

    it('muestra los filtros de período, programa y texto', () => {
        cy.get('#idPeriodo').should('be.visible')
        cy.get('#idPrograma').should('be.visible')
        cy.get('#texto').should('be.visible')
    })

    it('no permite buscar sin período y programa', () => {
        // La pantalla preselecciona un período al cargar; se quita para
        // probar el caso sin ninguno de los dos.
        cy.get('#idPeriodo').select('Seleccione un período')
        cy.contains('button', 'Buscar cursos').should('be.disabled')
        // Solo período
        cy.get('#idPeriodo').select(PERIODO)
        cy.contains('button', 'Buscar cursos').should('be.disabled')
        // Solo programa
        cy.get('#idPeriodo').select('Seleccione un período')
        cy.get('#idPrograma').select(PROGRAMA)
        cy.contains('button', 'Buscar cursos').should('be.disabled')
        // Ambos
        cy.get('#idPeriodo').select(PERIODO)
        cy.contains('button', 'Buscar cursos').should('be.enabled')
    })

    it('lista los cursos con horario, grupo, profesor y cupos', () => {
        buscar()
        ;['Código', 'Curso', 'Nivel', 'Grupo', 'Horario', 'Profesor', 'Cupos disponibles']
            .forEach((col) => cy.contains('th', col).should('be.visible'))
    })

    it('filtra por nombre del curso', () => {
        buscar()
        cy.get(TABLA).first().find('td').eq(1).invoke('text').then((nombre) => {
            const texto = nombre.trim()
            cy.get('#texto').type(texto)
            // El sistema filtra por nombre o código: cada fila debe coincidir en alguno de los dos.
            cy.get(TABLA).should('have.length.greaterThan', 0).each(($fila) => {
                const codigo = $fila.find('td').eq(0).text().toLowerCase()
                const curso = $fila.find('td').eq(1).text().toLowerCase()
                const buscado = texto.toLowerCase()
                expect(codigo.includes(buscado) || curso.includes(buscado),
                    `la fila "${codigo} ${curso}" coincide con "${texto}"`).to.be.true
            })
        })
    })

    it('informa cuando ningún curso coincide con el texto', () => {
        buscar()
        cy.get('#texto').type('zzzzzzzz')
        cy.contains('[role="status"]', 'Ningún curso coincide con "zzzzzzzz"').should('be.visible')
    })

    it('Limpiar borra el texto y descarta los resultados', () => {
        buscar()
        cy.get('#texto').type('abc')
        cy.contains('button', 'Limpiar').click()
        cy.get('#texto').should('have.value', '')
        cy.get('table.oferta-tabla').should('not.exist')
    })
})