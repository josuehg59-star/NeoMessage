package com.josuedev.neomessage

import java.text.Normalizer

/**
 * 👨‍🍳 RECETARIO INTELIGENTE INTEGRADO PARA NEO AI
 * Proporciona recetas completas y auténticas con ingredientes exactos,
 * tiempos, porciones, preparación paso a paso y consejos del chef.
 */
object NeoRecipeBook {

    data class Recipe(
        val title: String,
        val portions: String,
        val time: String,
        val ingredients: List<String>,
        val steps: List<String>,
        val chefTip: String
    )

    private fun clean(s: String): String {
        val nfd = Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD)
        return nfd.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    fun isRecipeQuery(query: String): Boolean {
        val q = clean(query)
        val triggers = listOf(
            "receta", "como hacer", "como preparar", "como cocino", "como cocinar",
            "ingredientes de", "ingredientes para", "modo de preparacion", "pasos para hacer",
            "como se hace", "como se prepara", "guiso de", "postre de", "receta de", "cocinar"
        )
        return triggers.any { q.contains(it) }
    }

    fun findRecipe(query: String): Recipe {
        val q = clean(query)

        // 1. Enchiladas verdes
        if (q.contains("enchilada") && (q.contains("verde") || q.contains("suiza") || !q.contains("roja"))) {
            return Recipe(
                title = "Enchiladas Verdes con Pollo",
                portions = "4 porciones (12 enchiladas)",
                time = "35 minutos",
                ingredients = listOf(
                    "12 tortillas de maíz del día o frías",
                    "1 pechuga de pollo cocida y deshebrada",
                    "750g de tomatillo verde (sin cáscara)",
                    "3 a 4 chiles serranos o jalapeños",
                    "1/2 cebolla blanca",
                    "2 dientes de ajo",
                    "1 manojo pequeño de cilantro fresco",
                    "1 taza de caldo de pollo",
                    "1 taza de crema ácida mexicana",
                    "200g de queso fresco o cotija desmoronado",
                    "Aceite vegetal para freír",
                    "Sal y pimienta al gusto"
                ),
                steps = listOf(
                    "Cocina los tomatillos y chiles en una olla con agua hirviendo durante 7-8 minutos hasta que cambien de color sin que se revienten.",
                    "Licúa los tomatillos, chiles, 1/4 de cebolla, ajo, cilantro y la taza de caldo de pollo con sal al gusto hasta lograr una salsa homogénea.",
                    "Calienta 1 cucharada de aceite en una sartén honda, vierte la salsa licuada y déjala sazonar a fuego medio por 5 minutos.",
                    "En otra sartén, pasa cada tortilla por aceite caliente unos 5 segundos por lado (solo para suavizar sin dorar) y escúrrelas.",
                    "Rellena cada tortilla con pollo deshebrado, enróllalas y acomódalas en un refractario o plato extendido.",
                    "Baña generosamente con la salsa verde caliente recién hecha.",
                    "Decora por encima con crema ácida, abundante queso fresco, rodajas de cebolla y ramitas de cilantro."
                ),
                chefTip = "Para que la salsa verde no quede ácida, no hiervas de más los tomatillos verdes y agrega una pizca de azúcar o caldo de pollo concentrado al licuar."
            )
        }

        // 2. Enchiladas rojas
        if (q.contains("enchilada") && q.contains("roja")) {
            return Recipe(
                title = "Enchiladas Rojas Tradicionales",
                portions = "4 porciones",
                time = "40 minutos",
                ingredients = listOf(
                    "12 tortillas de maíz",
                    "4 chiles guajillo secos desvenados",
                    "2 chiles ancho secos desvenados",
                    "2 jitomates maduros",
                    "1/4 de cebolla y 2 dientes de ajo",
                    "1 taza de caldo de pollo o agua",
                    "1 pechuga de pollo deshebrada o 300g de queso fresco",
                    "Crema ácida, queso cotija y lechuga picada para acompañar",
                    "Aceite vegetal y sal al gusto"
                ),
                steps = listOf(
                    "Hierve los chiles secos junto con los jitomates en agua durante 8 minutos hasta que estén suaves.",
                    "Licúa los chiles, jitomates, cebolla, ajo, sal y 1 taza de caldo. Cuela la salsa sobre una sartén caliente con poco aceite y cocina 6 minutos.",
                    "Pasa cada tortilla por aceite caliente unos segundos para suavizar.",
                    "Sumerge cada tortilla en la salsa roja caliente, rellénala con pollo o queso y enróllala.",
                    "Sirve en platos, baña con un poco más de salsa, agrega crema, queso espolvoreado y lechuga fresca."
                ),
                chefTip = "Colar la salsa de chile guajillo es indispensable para que quede tersa y sin pellejitos molestos."
            )
        }

        // 3. Pozole rojo
        if (q.contains("pozole") || q.contains("posole")) {
            return Recipe(
                title = "Pozole Rojo Tradicional de Cerdo",
                portions = "6 a 8 porciones",
                time = "2 horas (u olla exprés 50 min)",
                ingredients = listOf(
                    "1 kg de maíz pozolero nixtamalizado precocido",
                    "1 kg de carne de cerdo (espinazo, costilla y maciza cortada en trozos)",
                    "1 cabeza de ajo entera pelada",
                    "1 cebolla blanca entera grande",
                    "6 chiles guajillo desvenados y suavizados en agua caliente",
                    "2 chiles ancho desvenados y suavizados",
                    "1 cucharadita de orégano seco",
                    "Sal de grano al gusto",
                    "Acompañamientos: lechuga romana picada, rábanos rebanados, cebolla picada, orégano, chile piquín, limones y tostadas de maíz con crema."
                ),
                steps = listOf(
                    "Enjuaga muy bien el maíz precocido. Colócalo en una olla grande con agua abundante, media cebolla y media cabeza de ajo. Cocina hasta que el maíz floree o reviente.",
                    "Agrega los trozos de carne de cerdo y sal al gusto. Cocina a fuego medio-bajo hasta que la carne esté muy suave.",
                    "Licúa los chiles guajillo y ancho hidratados con la otra media cebolla, 3 dientes de ajo, orégano y un poco del agua de los chiles. Cuela la salsa.",
                    "Vierte la salsa roja colada a la olla del pozole. Mezcla bien y deja hervir a fuego bajo durante 25 minutos para que los sabores se integren.",
                    "Rectifica de sal y retira el exceso de grasa si lo deseas.",
                    "Sirve bien caliente en platos hondos con su carne y maíz, y acompáñalo en la mesa con lechuga, rábanos, cebolla, orégano, limón y tostadas."
                ),
                chefTip = "Cocer la carne junto con el maíz permite que el caldo adquiera un sabor profundo y concentrado."
            )
        }

        // 4. Mole poblano con pollo
        if (q.contains("mole")) {
            return Recipe(
                title = "Mole Poblano Auténtico con Pollo",
                portions = "6 porciones",
                time = "1 hora 15 minutos",
                ingredients = listOf(
                    "1 pollo entero en piezas cocido con cebolla, ajo y sal (reserva el caldo)",
                    "4 chiles mulato, 4 chiles ancho y 4 chiles pasilla limpios y desvenados",
                    "2 jitomates y 2 tomatillos verdes asados",
                    "1/2 plátano macho rebanado y frito",
                    "1 tortilla quemada tostada y 1/2 bolillo frito",
                    "2 cucharadas de ajonjolí tostado, 2 cucharadas de cacahuates y 2 de pasas",
                    "1 raja de canela, 2 clavos de olor y pizca de comino",
                    "1 tableta de chocolate de mesa (tipo Abuelita)",
                    "Manteca de cerdo o aceite vegetal y sal al gusto"
                ),
                steps = listOf(
                    "Pasa los chiles secos por manteca o aceite muy caliente rápidamente sin quemarlos y remójalos en caldo de pollo caliente.",
                    "Fríe en la misma grasa los cacahuates, pasas, ajonjolí, tortilla, bolillo, plátano macho y especias.",
                    "Licúa en tandas todos los ingredientes fritos, los chiles remojados, jitomates asados y tomatillos con suficiente caldo de pollo hasta lograr una pasta fina y tersa.",
                    "Calienta manteca en una cazuela de barro o sartén honda, vierte la salsa y sofríela a fuego medio sin dejar de mover.",
                    "Agrega la tablilla de chocolate en trozos y más caldo de pollo hasta obtener la consistencia deseada.",
                    "Cocina a fuego lento durante 20 minutos hasta que la grasa suba a la superficie.",
                    "Agrega las piezas de pollo cocidas y cocina 5 minutos más. Sirve con arroz rojo y espolvorea ajonjolí tostado."
                ),
                chefTip = "El secreto de un buen mole está en dorar los ingredientes con paciencia y no quemar los chiles secos para evitar que amargue."
            )
        }

        // 5. Chilaquiles verdes o rojos
        if (q.contains("chilaquil")) {
            val isRojo = q.contains("rojo")
            return Recipe(
                title = if (isRojo) "Chilaquiles Rojos Crujientes" else "Chilaquiles Verdes Tradicionales",
                portions = "3 a 4 porciones",
                time = "25 minutos",
                ingredients = listOf(
                    "12 a 15 tortillas de maíz cortadas en triángulos (totopos)",
                    if (isRojo) "4 jitomates, 2 chiles serranos y 1/4 cebolla" else "600g de tomatillo verde, 3 chiles serranos y 1 manojo de cilantro",
                    "1 taza de caldo de pollo o agua",
                    "1 diente de ajo",
                    "1 taza de pechuga de pollo deshebrada o 2 huevos estrellados por persona",
                    "Crema ácida, queso fresco desmoronado y cebolla morada rebanada",
                    "Aceite abundante para freír totopos y sal al gusto"
                ),
                steps = listOf(
                    "Deja secar los triángulos de tortilla al aire libre 1 hora y fríelos en aceite bien caliente hasta que estén dorados y crujientes. Escurre en papel absorbente.",
                    "Hierve los ingredientes de la salsa (jitomate o tomatillo con chiles). Licúa con cebolla, ajo y cilantro.",
                    "Sazona la salsa en una sartén amplia con 1 cucharada de aceite caliente durante 5 minutos a fuego medio.",
                    "Agrega los totopos directamente a la salsa hirviendo y mezcla suavemente durante solo 1 o 2 minutos para que queden crocantes (o sirve los totopos en el plato y báñalos con la salsa si te gustan extra crujientes).",
                    "Sirve de inmediato en el plato, añade pollo o huevo encima, y corona con crema, queso fresco y cebolla."
                ),
                chefTip = "Si dejas orear las tortillas cortadas antes de freírlas, absorberán mucho menos aceite y quedarán mucho más crujientes."
            )
        }

        // 6. Arroz rojo mexicano
        if (q.contains("arroz") && (q.contains("rojo") || q.contains("mexicano") || !q.contains("leche"))) {
            return Recipe(
                title = "Arroz Rojo Mexicano Esponjoso",
                portions = "4 a 5 porciones",
                time = "30 minutos",
                ingredients = listOf(
                    "1 taza de arroz blanco de grano largo",
                    "2 tazas de agua caliente o caldo de pollo",
                    "2 jitomates maduros",
                    "1/4 de cebolla blanca",
                    "1 diente de ajo",
                    "1 zanahoria picada en cubitos y 1/2 taza de chícharos (opcional)",
                    "3 cucharadas de aceite vegetal",
                    "1 chile serrano entero (opcional para aroma)",
                    "1 cucharadita de sal"
                ),
                steps = listOf(
                    "Enjuaga el arroz con agua tibia en un colador hasta que el agua salga clara y déjalo escurrir completamente.",
                    "Licúa los 2 jitomates con la cebolla, el diente de ajo y 1/4 taza de agua hasta que quede perfectamente molido.",
                    "Calienta el aceite en una cacerola con tapa a fuego medio. Agrega el arroz escurrido y sofríe moviendo constantemente hasta que adquiera un color dorado claro y suene arenoso.",
                    "Vierte el jitomate molido sobre el arroz dorado (pasándolo por colador si prefieres) y deja freír 2 minutos hasta que el líquido se reduzca a la mitad.",
                    "Agrega las 2 tazas de caldo de pollo caliente, la verdura, el chile serrano entero y la sal. Mezcla una sola vez.",
                    "Tapa la cacerola, baja la flama al mínimo absoluto y cocina durante 18-20 minutos SIN DESTAPAR.",
                    "Apaga el fuego, retira la tapa y deja reposar 5 minutos. Con un tenedor remueve suavemente de abajo hacia arriba para esponjar."
                ),
                chefTip = "La regla de oro para que el arroz no quede batido es: 1 taza de arroz por 2 tazas de líquido caliente, y nunca revolverlo mientras se está cociendo."
            )
        }

        // 7. Albóndigas en salsa de chipotle
        if (q.contains("albondiga") || q.contains("almondiga")) {
            return Recipe(
                title = "Albóndigas Caseras en Salsa de Chipotle",
                portions = "5 porciones (15 albóndigas)",
                time = "45 minutos",
                ingredients = listOf(
                    "500g de carne molida mixta (res y cerdo)",
                    "1 huevo entero",
                    "1/3 taza de arroz blanco crudo remojado 15 minutos en agua tibia",
                    "2 cucharadas de pan molido",
                    "1 diente de ajo finamente picado y 1/4 cebolla picada",
                    "1 cucharadita de hierbabuena fresca picada (toque clásico mexicano)",
                    "4 jitomates maduros hervidos",
                    "2 chiles chipotles de lata",
                    "2 papas y 2 zanahorias cortadas en cubos",
                    "1 litro de caldo de res o agua con sal",
                    "Sal y pimienta al gusto"
                ),
                steps = listOf(
                    "En un tazón mezcla la carne molida con el huevo, el arroz escurrido, pan molido, ajo picado, cebolla picada, hierbabuena, sal y pimienta.",
                    "Forma bolitas compactas del tamaño de una pelota de golf y resérvalas.",
                    "Licúa los jitomates hervidos con los chiles chipotles, 1 trozo de cebolla, 1 diente de ajo y 1 taza de caldo.",
                    "En una cacerola amplia calienta 1 cucharada de aceite, vierte la salsa y sofríela por 5 minutos.",
                    "Agrega el resto del caldo de res, las papas y zanahorias, y deja que comience a hervir.",
                    "Con el caldo hirviendo a fuego medio, introduce cuidadosamente las albóndigas una por una.",
                    "Tapa la cacerola y cocina a fuego medio-bajo durante 25 a 30 minutos hasta que la carne y el arroz dentro de las albóndigas estén completamente cocidos y suaves.",
                    "Sirve en platos hondos con abundante caldillo, verduras y tortillas calientes."
                ),
                chefTip = "Agregar arroz crudo remojado y un toque de hierbabuena a la carne picada les da una textura jugosa y el aroma inconfundible de la cocina tradicional mexicana."
            )
        }

        // 8. Caldo de res / Puchero
        if (q.contains("caldo de res") || q.contains("puchero") || q.contains("cocido de res")) {
            return Recipe(
                title = "Caldo de Res Tradicional con Verduras",
                portions = "6 porciones",
                time = "1 hora 45 minutos (u olla de presión 45 min)",
                ingredients = listOf(
                    "1 kg de carne de res (chamberete con tuétano, costilla o pescuezo cortado en trozos)",
                    "2 elotes frescos cortados en rodajas gruesas",
                    "2 chayotes pelados y cortados en cuartos",
                    "3 zanahorias rebanadas en trozos grandes",
                    "2 calabacitas cortadas en rodajas gruesas",
                    "1/4 de col o repollo cortado en trozos",
                    "1 cabeza de ajo entera pelada y 1 cebolla partida por la mitad",
                    "1 manojo de cilantro fresco y 2 ramitas de hierbabuena",
                    "Sal de grano al gusto",
                    "Limones, arroz rojo y salsa de chile piquín para acompañar"
                ),
                steps = listOf(
                    "En una olla grande coloca la carne de res con suficiente agua (unos 3 litros), la cebolla, el ajo y 1 cucharada de sal de grano.",
                    "Lleva a ebullición y con una cuchara retira toda la espuma e impurezas que suban a la superficie.",
                    "Agrega los elotes en rodajas, tapa y cocina a fuego medio durante 1 hora (o 30 min en olla exprés) hasta que la carne comience a suavizarse.",
                    "Agrega el chayote y la zanahoria (verduras duras). Cocina 10 minutos.",
                    "Agrega la calabacita, la col, el manojo de cilantro y la hierbabuena. Cocina 10 minutos más a fuego suave para que las calabacitas queden tiernas sin deshacerse.",
                    "Rectifica de sal.",
                    "Sirve en platos hondos con su trozo de carne, tuétano, verduras variadas y caldo caliente. Acompaña con limón y tortillas."
                ),
                chefTip = "Espumar el caldo al momento que comienza a hervir es el truco para que el consomé quede cristalino, limpio y delicioso."
            )
        }

        // 9. Chiles rellenos de queso
        if (q.contains("chile relleno") || q.contains("chiles rellenos")) {
            return Recipe(
                title = "Chiles Rellenos de Queso Capeados en Caldillo",
                portions = "4 porciones (4 chiles)",
                time = "45 minutos",
                ingredients = listOf(
                    "4 chiles poblanos grandes y rectos",
                    "350g de queso panela, oaxaca o fresco cortado en barras",
                    "3 huevos (claras y yemas separadas)",
                    "1/2 taza de harina de trigo",
                    "4 jitomates maduros hervidos",
                    "1/4 cebolla y 1 diente de ajo",
                    "Aceite vegetal abundante para freír",
                    "Palillos de madera para cerrar",
                    "Sal y orégano al gusto"
                ),
                steps = listOf(
                    "Asa los chiles poblanos directamente sobre la flama de la estufa hasta que la piel esté completamente ampollada y negra.",
                    "Mételos de inmediato a una bolsa de plástico cerrada durante 10 minutos para que suden.",
                    "Pela los chiles con los dedos o una cuchara, haz una incisión longitudinal y retira con cuidado las semillas y venas sin romper el chile.",
                    "Rellena cada chile con generosas barras de queso y ciérralos con un palillo de madera.",
                    "Pasa cada chile por harina sacudiendo el exceso (esto ayuda a que el huevo se pegue).",
                    "Bate las claras de huevo a punto de nieve (hasta que formen picos firmes) y añade las yemas suavemente con una pizca de sal.",
                    "Pasa los chiles enharinados por el huevo batido y fríelos en una sartén con aceite caliente a fuego medio hasta que el capeado esté dorado y esponjoso.",
                    "Para el caldillo: Licúa los jitomates con cebolla, ajo y sal. Sofríelo 8 minutos con un toque de orégano.",
                    "Sirve los chiles bañados en el caldillo de jitomate caliente."
                ),
                chefTip = "Enharinar perfectamente el chile seco antes de sumergirlo en el huevo garantiza que el capeado jamás se desprenda."
            )
        }

        // 10. Pay de limón casero (sin horno)
        if (q.contains("pay de limon") || q.contains("pie de limon") || q.contains("carlota")) {
            return Recipe(
                title = "Pay de Limón Frío (Carlota con Galletas Marías)",
                portions = "8 porciones",
                time = "15 minutos (+ 2 horas refrigeración)",
                ingredients = listOf(
                    "1 lata de leche condensada (397g, tipo La Lechera)",
                    "1 lata de leche evaporada (360g, tipo Carnation)",
                    "1/2 taza de jugo de limón recién exprimido y colado (unos 5 limones)",
                    "1 o 2 paquetes de galletas Marías (unos 170g)",
                    "Ralladura de 1 limón verde para decorar (sin parte blanca)"
                ),
                steps = listOf(
                    "En la licuadora vierte la leche condensada y la leche evaporada.",
                    "Enciende la licuadora a velocidad media y, mientras está licuando, vierte lentamente el jugo de limón en forma de hilo.",
                    "Verás que casi de inmediato la mezcla se espesa mágicamente por la reacción del ácido cítrico con la leche. Licúa 30 segundos más hasta que quede bien cremosa.",
                    "En un refractario de vidrio, coloca una capa base de galletas Marías cubriendo todo el fondo.",
                    "Vierte una capa de la crema de limón sobre las galletas y extiéndela parejo con una espátula.",
                    "Repite formando capas alternadas de galleta y crema, terminando con crema en la parte superior.",
                    "Decora espolvoreando galleta molida y ralladura fina de cáscara de limón verde.",
                    "Refrigera por lo menos durante 2 a 3 horas (o 45 minutos en congelador) para que tome consistencia firme y las galletas se humedezcan.",
                    "Corta en cuadros y sirve bien frío."
                ),
                chefTip = "Verter el jugo de limón poco a poco con la licuadora andando es el secreto para lograr una consistencia tan espesa que parece mousse sin necesidad de grenetina ni horno."
            )
        }

        // 11. Guacamole tradicional
        if (q.contains("guacamole") || q.contains("guacamol")) {
            return Recipe(
                title = "Guacamole Tradicional en Molcajete",
                portions = "4 porciones",
                time = "10 minutos",
                ingredients = listOf(
                    "3 aguacates Hass maduros pero firmes",
                    "1/2 cebolla blanca finamente picada",
                    "1 o 2 chiles serranos finamente picados",
                    "1 jitomate maduro sin semillas picado en cubitos",
                    "1/4 taza de hojas de cilantro fresco picadas",
                    "El jugo de 1 limón verde",
                    "Sal de grano al gusto",
                    "Totopos de maíz crujientes para servir"
                ),
                steps = listOf(
                    "En un molcajete o tazón, machaca primero un poco de cebolla picada, chile serrano y una pizca de sal de grano para liberar sus aceites y aromas.",
                    "Corta los aguacates por la mitad, retira el hueso y saca la pulpa con una cuchara.",
                    "Machaca el aguacate con un tenedor dejando trocitos enteros rústicos (no lo hagas puré liso).",
                    "Agrega el jitomate picado, el resto de la cebolla, el cilantro fresco y el jugo de limón.",
                    "Mezcla con suavidad para integrar todos los colores y rectifica de sal.",
                    "Sirve de inmediato acompañado de totopos de maíz recién hechos."
                ),
                chefTip = "No licúes el guacamole; la textura rústica con tropezones de aguacate y el jugo de limón fresco evitan que se oxide y le dan su sabor auténtico."
            )
        }

        // 12. Sopa de tortilla / Sopa Azteca
        if (q.contains("sopa de tortilla") || q.contains("sopa azteca")) {
            return Recipe(
                title = "Sopa de Tortilla Tradicional (Sopa Azteca)",
                portions = "4 porciones",
                time = "30 minutos",
                ingredients = listOf(
                    "10 tortillas de maíz cortadas en tiras delgadas",
                    "4 jitomates maduros asados",
                    "1/4 de cebolla asada y 1 diente de ajo",
                    "1 chile pasilla seco cortado en aritos y frito brevemente",
                    "1 litro de caldo de pollo",
                    "1 ramita de epazote fresco",
                    "Aceite para freír",
                    "Para servir: aguacate en cubos, queso panela en cubos, crema ácida y chicharrón de cerdo troceado"
                ),
                steps = listOf(
                    "Fríe las tiras de tortilla en aceite bien caliente hasta que estén bien doradas y crujientes. Escúrrelas sobre papel absorbente.",
                    "En el mismo aceite pasa los aritos de chile pasilla por solo 3 segundos y retíralos de inmediato.",
                    "Licúa los jitomates asados con la cebolla, el ajo y 1 taza de caldo de pollo. Cuela la salsa.",
                    "Calienta 1 cucharada de aceite en una olla, vierte el caldillo y cocina a fuego medio por 5 minutos.",
                    "Agrega el resto del caldo de pollo y la ramita de epazote. Deja hervir a fuego bajo durante 12 minutos.",
                    "Para servir: en cada plato hondo coloca una porción generosa de tiras de tortilla dorada.",
                    "Vierte el caldo de jitomate bien caliente sobre las tortillas.",
                    "Decora de inmediato con crema, cubitos de queso panela, aguacate, aritos de chile pasilla frito y trocitos de chicharrón."
                ),
                chefTip = "Nunca cocines las tortillas dentro del caldo; agrégalas en el plato al momento de servir para que se mantengan crujientes y deliciosas."
            )
        }

        // 13. Bistec a la mexicana
        if (q.contains("bistec") || q.contains("bisteck") || q.contains("mexicana")) {
            return Recipe(
                title = "Bistec a la Mexicana Jugoso",
                portions = "4 porciones",
                time = "25 minutos",
                ingredients = listOf(
                    "500g de bistec de res (diezmillo o pulpa negra cortada en fajitas o cubos)",
                    "3 jitomates maduros picados en cubitos",
                    "1 cebolla blanca rebanada en medias lunas",
                    "2 chiles serranos en rodajas (al gusto)",
                    "2 dientes de ajo finamente picados",
                    "1/2 taza de agua o caldo de res",
                    "2 cucharadas de aceite vegetal",
                    "Sal, pimienta negra molida y pizca de comino",
                    "Frijoles refritos y tortillas para acompañar"
                ),
                steps = listOf(
                    "Sazona la carne cortada con sal, pimienta negra y ajo picado.",
                    "En una sartén amplia calienta el aceite a fuego alto. Agrega la carne y séllala durante 5 minutos hasta que cambie de color y empiece a dorar.",
                    "Agrega la cebolla y los chiles serranos en rodajas. Cocina moviendo durante 3 minutos hasta que la cebolla esté transparente.",
                    "Añade el jitomate picado y la pizca de comino. Cocina 4 minutos hasta que el jitomate suelte sus jugos.",
                    "Agrega la 1/2 taza de caldo o agua, baja el fuego a medio-bajo, tapa y cocina 10 minutos para que la carne quede muy suave y con un caldillo delicioso.",
                    "Rectifica de sal.",
                    "Sirve caliente con frijoles refritos y tortillas de maíz recién salidas del comal."
                ),
                chefTip = "Sellar primero la carne a fuego alto antes de agregar las verduras retiene todos sus jugos naturales."
            )
        }

        // Default dinámico para cualquier otro platillo solicitado
        val dishName = q.replace("receta de", "")
            .replace("receta", "")
            .replace("como hacer", "")
            .replace("como preparar", "")
            .replace("como cocinar", "")
            .replace("como se hace", "")
            .replace("ingredientes de", "")
            .trim()
            .replaceFirstChar { it.uppercase() }
            .ifEmpty { "Platillo Casero" }

        return Recipe(
            title = dishName,
            portions = "4 porciones",
            time = "35 a 45 minutos",
            ingredients = listOf(
                "Ingrediente principal fresco (proteína o vegetal base de $dishName)",
                "Cebolla blanca y 2 dientes de ajo finamente picados",
                "Jitomate o base aromática fresca para sazón",
                "Hierbas de olor (laurel, tomillo, orégano o cilantro según el estilo)",
                "Aceite vegetal o mantequilla para saltear",
                "Caldo o agua para cocción equilibrada",
                "Sal de grano y pimienta negra recién molida al gusto"
            ),
            steps = listOf(
                "Limpia y corta los ingredientes frescos en porciones uniformes para una cocción pareja.",
                "En una sartén o cacerola caliente con un poco de aceite, sofríe la cebolla y el ajo hasta que acitronen y suelten su aroma.",
                "Agrega el ingrediente principal de $dishName y séllalo a fuego medio-alto para concentrar todos sus sabores.",
                "Incorpora las verduras, salsa o caldillo base y las hierbas aromáticas deseadas.",
                "Baja a fuego medio-bajo, tapa la cacerola y deja cocinar a fuego lento durante 20 a 25 minutos para que los sabores maduren y alcancen la textura ideal.",
                "Prueba el sazón y rectifica de sal y pimienta al gusto antes de apagar.",
                "Deja reposar 5 minutos tapado antes de servir bien caliente con tu guarnición favorita."
            ),
            chefTip = "Cocinar a fuego lento con tapa permite que los jugos naturales se concentren y la consistencia quede tierna y llena de sabor."
        )
    }

    fun formatRecipeResponse(recipe: Recipe): String {
        val sb = StringBuilder("🍳 *RECETA COMPLETA: ${recipe.title.uppercase()}*\n\n")
        sb.append("⏱️ *Tiempo estimado:* ${recipe.time}\n")
        sb.append("👥 *Rendimiento:* ${recipe.portions}\n\n")

        sb.append("🛒 *INGREDIENTES EXACTOS:*\n")
        recipe.ingredients.forEach { ing ->
            sb.append(" • $ing\n")
        }

        sb.append("\n👩‍🍳 *PREPARACIÓN PASO A PASO:*\n")
        recipe.steps.forEachIndexed { index, step ->
            sb.append("\n*${index + 1}.* $step\n")
        }

        sb.append("\n💡 *CONSEJO DEL CHEF:*\n")
        sb.append("_${recipe.chefTip}_\n\n")
        sb.append("¡Buen provecho! Si deseas ajustar porciones, cambiar un ingrediente o conocer variantes, solo dímelo.")
        return sb.toString()
    }
}
