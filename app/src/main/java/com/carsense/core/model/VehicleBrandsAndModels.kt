package com.carsense.core.model

/**
 * Provides common car makes and models data for the application.
 */
object VehicleBrandsAndModels {

    /**
     * List of car makes/brands alphabetically ordered.
     */
    val carMakes = listOf(
        "Acura",
        "Alfa Romeo",
        "Aston Martin",
        "Audi",
        "Bentley",
        "BMW",
        "Bugatti",
        "Buick",
        "Cadillac",
        "Chevrolet",
        "Chrysler",
        "Citroën",
        "Cupra",
        "Dacia",
        "Dodge",
        "DS Automobiles",
        "Ferrari",
        "Fiat",
        "Ford",
        "Genesis",
        "GMC",
        "Honda",
        "Hyundai",
        "Infiniti",
        "Jaguar",
        "Jeep",
        "Kia",
        "Lamborghini",
        "Lancia",
        "Land Rover",
        "Lexus",
        "Lincoln",
        "Maserati",
        "Mazda",
        "Mercedes-Benz",
        "Mini",
        "Mitsubishi",
        "Nissan",
        "Opel",
        "Peugeot",
        "Porsche",
        "Ram",
        "Renault",
        "Rolls-Royce",
        "SEAT",
        "Škoda",
        "Subaru",
        "Suzuki",
        "Tesla",
        "Toyota",
        "Vauxhall",
        "Volkswagen",
        "Volvo"
    )

    /**
     * Map of car makes to their available models.
     */
    val carModelsMap = mapOf(
        "Acura" to listOf("ILX", "MDX", "RDX", "TLX", "NSX"),
        "Alfa Romeo" to listOf("Giulia", "Stelvio", "4C", "Tonale", "Giulietta", "MiTo"),
        "Aston Martin" to listOf("DB11", "DBS", "Vantage", "DBX", "Valkyrie"),
        "Audi" to listOf(
            "A1",
            "A3",
            "A4",
            "A5",
            "A6",
            "A7",
            "A8",
            "Q2",
            "Q3",
            "Q5",
            "Q7",
            "Q8",
            "e-tron",
            "TT",
            "R8",
            "RS3",
            "RS4",
            "RS6",
            "RS7"
        ),
        "Bentley" to listOf("Continental GT", "Bentayga", "Flying Spur", "Mulsanne"),
        "BMW" to listOf(
            "1 Series",
            "2 Series",
            "3 Series",
            "4 Series",
            "5 Series",
            "6 Series",
            "7 Series",
            "8 Series",
            "X1",
            "X2",
            "X3",
            "X4",
            "X5",
            "X6",
            "X7",
            "Z4",
            "i3",
            "i4",
            "i8",
            "iX",
            "M2",
            "M3",
            "M4",
            "M5",
            "M8"
        ),
        "Bugatti" to listOf("Chiron", "Veyron", "Divo", "Centodieci"),
        "Buick" to listOf("Enclave", "Encore", "Envision", "LaCrosse", "Regal"),
        "Cadillac" to listOf("ATS", "CT4", "CT5", "CTS", "Escalade", "XT4", "XT5", "XT6"),
        "Chevrolet" to listOf(
            "Blazer",
            "Bolt",
            "Camaro",
            "Colorado",
            "Corvette",
            "Equinox",
            "Impala",
            "Malibu",
            "Silverado",
            "Spark",
            "Suburban",
            "Tahoe",
            "Traverse",
            "Trax"
        ),
        "Chrysler" to listOf("300", "Pacifica", "Voyager"),
        "Citroën" to listOf(
            "C1",
            "C3",
            "C3 Aircross",
            "C4",
            "C4 Cactus",
            "C5",
            "C5 Aircross",
            "Berlingo",
            "SpaceTourer"
        ),
        "Cupra" to listOf("Formentor", "Leon", "Ateca", "Born", "Terramar"),
        "Dacia" to listOf("Sandero", "Duster", "Logan", "Spring", "Jogger", "Dokker", "Lodgy"),
        "Dodge" to listOf("Challenger", "Charger", "Durango", "Grand Caravan", "Journey"),
        "DS Automobiles" to listOf("DS 3", "DS 3 Crossback", "DS 4", "DS 7 Crossback", "DS 9"),
        "Ferrari" to listOf("488", "812", "F8", "Roma", "SF90", "296 GTB", "Purosangue"),
        "Fiat" to listOf(
            "500", "500X", "500e", "Panda", "Tipo", "Spider", "Ducato", "Doblo", "Multipla"
        ),
        "Ford" to listOf(
            "Bronco",
            "EcoSport",
            "Edge",
            "Escape",
            "Expedition",
            "Explorer",
            "F-150",
            "F-250",
            "F-350",
            "Fiesta",
            "Focus",
            "Fusion",
            "Mustang",
            "Ranger",
            "Transit",
            "Kuga",
            "Puma",
            "Mondeo",
            "S-Max",
            "Galaxy"
        ),
        "Genesis" to listOf("G70", "G80", "G90", "GV70", "GV80"),
        "GMC" to listOf("Acadia", "Canyon", "Sierra", "Terrain", "Yukon"),
        "Honda" to listOf(
            "Accord",
            "Civic",
            "CR-V",
            "Fit",
            "HR-V",
            "Insight",
            "Odyssey",
            "Passport",
            "Pilot",
            "Ridgeline",
            "Jazz",
            "e"
        ),
        "Hyundai" to listOf(
            "Accent",
            "Elantra",
            "Kona",
            "Palisade",
            "Santa Fe",
            "Sonata",
            "Tucson",
            "Veloster",
            "Venue",
            "i10",
            "i20",
            "i30",
            "IONIQ",
            "IONIQ 5",
            "IONIQ 6",
            "Bayon"
        ),
        "Infiniti" to listOf("Q30", "Q50", "Q60", "QX30", "QX50", "QX60", "QX80"),
        "Jaguar" to listOf("E-Pace", "F-Pace", "F-Type", "I-Pace", "XE", "XF", "XJ"),
        "Jeep" to listOf(
            "Cherokee",
            "Compass",
            "Gladiator",
            "Grand Cherokee",
            "Renegade",
            "Wrangler",
            "Avenger",
            "Commander"
        ),
        "Kia" to listOf(
            "Ceed",
            "Forte",
            "K5",
            "Niro",
            "Optima",
            "Picanto",
            "Rio",
            "Seltos",
            "Sorento",
            "Soul",
            "Sportage",
            "Stinger",
            "Telluride",
            "ProCeed",
            "XCeed",
            "EV6",
            "EV9"
        ),
        "Lamborghini" to listOf("Aventador", "Huracan", "Urus", "Revuelto"),
        "Lancia" to listOf("Ypsilon", "Delta"),
        "Land Rover" to listOf(
            "Defender",
            "Discovery",
            "Discovery Sport",
            "Range Rover",
            "Range Rover Evoque",
            "Range Rover Sport",
            "Range Rover Velar"
        ),
        "Lexus" to listOf("ES", "GS", "GX", "IS", "LC", "LS", "LX", "NX", "RC", "RX", "UX"),
        "Lincoln" to listOf("Aviator", "Corsair", "MKZ", "Nautilus", "Navigator"),
        "Maserati" to listOf("Ghibli", "Levante", "Quattroporte", "MC20", "Grecale"),
        "Mazda" to listOf(
            "CX-3",
            "CX-30",
            "CX-5",
            "CX-60",
            "CX-9",
            "Mazda2",
            "Mazda3",
            "Mazda6",
            "MX-5 Miata",
            "MX-30"
        ),
        "Mercedes-Benz" to listOf(
            "A-Class",
            "B-Class",
            "C-Class",
            "CLA",
            "CLS",
            "E-Class",
            "EQA",
            "EQB",
            "EQC",
            "EQE",
            "EQS",
            "G-Class",
            "GLA",
            "GLB",
            "GLC",
            "GLE",
            "GLS",
            "S-Class",
            "SL",
            "SLC",
            "AMG GT",
            "AMG One"
        ),
        "Mini" to listOf("Clubman", "Convertible", "Countryman", "Hardtop", "Electric"),
        "Mitsubishi" to listOf(
            "ASX", "Eclipse Cross", "Mirage", "Outlander", "Outlander Sport", "Pajero", "Space Star"
        ),
        "Nissan" to listOf(
            "Altima",
            "Ariya",
            "Armada",
            "Frontier",
            "GT-R",
            "Juke",
            "Kicks",
            "Leaf",
            "Maxima",
            "Micra",
            "Murano",
            "Note",
            "Pathfinder",
            "Pulsar",
            "Qashqai",
            "Rogue",
            "Sentra",
            "Titan",
            "Versa",
            "X-Trail"
        ),
        "Opel" to listOf(
            "Astra",
            "Corsa",
            "Crossland",
            "Grandland",
            "Insignia",
            "Mokka",
            "Combo",
            "Vivaro",
            "Zafira"
        ),
        "Peugeot" to listOf(
            "108",
            "208",
            "2008",
            "308",
            "3008",
            "408",
            "508",
            "5008",
            "Rifter",
            "Traveller",
            "Partner",
            "Expert"
        ),
        "Porsche" to listOf("718", "911", "Cayenne", "Macan", "Panamera", "Taycan"),
        "Ram" to listOf("1500", "2500", "3500", "ProMaster"),
        "Renault" to listOf(
            "Captur",
            "Clio",
            "Espace",
            "Kadjar",
            "Kangoo",
            "Koleos",
            "Megane",
            "Scenic",
            "Talisman",
            "Twingo",
            "ZOE",
            "Arkana",
            "Austral"
        ),
        "Rolls-Royce" to listOf("Cullinan", "Dawn", "Ghost", "Phantom", "Wraith"),
        "SEAT" to listOf("Alhambra", "Arona", "Ateca", "Ibiza", "Leon", "Tarraco", "Mii"),
        "Škoda" to listOf(
            "Citigo",
            "Fabia",
            "Kamiq",
            "Karoq",
            "Kodiaq",
            "Octavia",
            "Rapid",
            "Scala",
            "Superb",
            "Enyaq iV"
        ),
        "Subaru" to listOf(
            "Ascent", "BRZ", "Crosstrek", "Forester", "Impreza", "Legacy", "Outback", "WRX"
        ),
        "Suzuki" to listOf(
            "Across", "Baleno", "Ignis", "Jimny", "Swift", "S-Cross", "Swace", "Vitara"
        ),
        "Tesla" to listOf("Model 3", "Model S", "Model X", "Model Y", "Cybertruck", "Roadster"),
        "Toyota" to listOf(
            "4Runner",
            "86",
            "Avalon",
            "Aygo",
            "C-HR",
            "Camry",
            "Corolla",
            "Highlander",
            "Land Cruiser",
            "Mirai",
            "Prius",
            "RAV4",
            "Sienna",
            "Supra",
            "Tacoma",
            "Tundra",
            "Yaris"
        ),
        "Vauxhall" to listOf(
            "Astra",
            "Corsa",
            "Crossland",
            "Grandland",
            "Insignia",
            "Mokka",
            "Combo",
            "Vivaro",
            "Zafira"
        ),
        "Volkswagen" to listOf(
            "Arteon",
            "Atlas",
            "Atlas Cross Sport",
            "Golf",
            "Golf GTI",
            "ID.3",
            "ID.4",
            "ID.5",
            "Jetta",
            "Passat",
            "Polo",
            "T-Cross",
            "T-Roc",
            "Taigo",
            "Tiguan",
            "Touareg",
            "Touran",
            "Up!"
        ),
        "Volvo" to listOf("C40", "S60", "S90", "V60", "V90", "XC40", "XC60", "XC90", "EX30", "EX90")
    )

    /**
     * Common fuel type options.
     */
    val fuelTypeOptions = listOf("Gasoline", "Diesel", "Electric", "Hybrid", "Hydrogen", "Other")

    /**
     * Common transmission type options.
     */
    val transmissionOptions = listOf("Automatic", "Manual", "CVT", "Semi-Automatic", "Dual-Clutch")

    /**
     * Common drivetrain options.
     */
    val drivetrainOptions = listOf("FWD", "RWD", "AWD", "4WD")

    /**
     * Get models for specific make.
     *
     * @param make The car make to get models for
     * @return List of models for the specified make, or empty list if make not found
     */
    fun getModelsForMake(make: String): List<String> {
        return carModelsMap[make] ?: emptyList()
    }
} 