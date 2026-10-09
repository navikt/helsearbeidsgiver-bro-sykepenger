# helsearbeidsgiver-bro-sykepenger
Mellomledd mellom HAG og SAS (sparkel-arbeidsgiver)

Tar imot nye forespørsler om Inntektsmelding som SAS sender på kafka-topic arbeidsgiveropplysninger, og oppdateringer på disse. 
Oppdateringer: SAS gir beskjed om at en forespørsel er forkastet eller besvart (BESVART_SPLEIS)
Videresender disse eventene inn til HAG på kafka-topic PRI som konsumeres av Simba (im-helsebro) og LPS-API, slik at forespørselen om inntektsmelding tilgjengeliggjøres eller oppdateres for Arbeidsgiver gjennom LPS-API, dialogporten og Fager. 

Leser meldinger fra HAG på PRI-topic:

-Forespørsel besvart. Dette sendes fra Simba når en inntektsmelding besvarer en forespørsel. Eventet oppdaterer state på forespørsel til BESVART_SIMBA

-Hent forespørsel (forespørselID). Sendes fra Simba når en Arbeidsgiver skal vise forespørselen / få et ferdigutfylt forslag til inntektsmelding.

-Hent eksponerte forespørsler (fnr). Sendes fra HAG-Admin når vi skal søke opp en forespørsel (tilhørende sak / oppgave) og vi bare har fnr til den sykmeldte.

-Forkast forespørsel manuelt (forespørselID). Sendes fra HAG-Admin når en ansatt (saksbehandler) etter samtale med arbeidsgiver avgjør at en forespørsel ikke skal svares ut med inntektsmelding. 
F eks hvis saken er avsluttet, eller fraværet kun gjelder AGP. 
