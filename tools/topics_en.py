"""English topics. Pairs are separated by "|", the two words of a pair by "/"."""
LANG = "en"
WW = {
    "Food": """
Pizza/Burger|Spaghetti/Lasagna|Pancakes/Waffles|Sushi/Sashimi|Tacos/Burritos|Fried chicken/Chicken nuggets|Hot dog/Corn dog
|Steak/Barbecue ribs|Ramen/Udon|Fried rice/Pad thai|Dumplings/Spring rolls|Sandwich/Wrap|Bagel/Donut|Croissant/Muffin
|Ice cream/Frozen yogurt|Chocolate/Caramel|Cookies/Brownies|Cheesecake/Tiramisu|Popcorn/Potato chips|French fries/Onion rings
|Apple/Pear|Orange/Grapefruit|Strawberry/Raspberry|Banana/Mango|Watermelon/Melon|Grapes/Blueberries|Tomato/Red pepper
|Carrot/Sweet potato|Lettuce/Cabbage|Broccoli/Cauliflower|Mushroom/Onion|Salmon/Tuna|Shrimp/Crab|Bacon/Ham
|Ketchup/Mustard|Mayonnaise/Ranch dressing|Salt/Sugar|Butter/Margarine|Cereal/Oatmeal|Omelet/Scrambled eggs
|Soup/Stew|Curry/Chili|Mac and cheese/Grilled cheese|Burrito bowl/Poke bowl|Birthday cake/Cupcake|Candy/Gum
""",
    "Drinks": """
Coffee/Tea|Coke/Pepsi|Orange juice/Apple juice|Milk/Soy milk|Water/Sparkling water|Beer/Wine|Latte/Cappuccino
|Smoothie/Milkshake|Energy drink/Sports drink|Hot chocolate/Warm milk|Lemonade/Iced tea|Bubble tea/Iced coffee|Whiskey/Vodka
""",
    "Daily life": """
Toothbrush/Toothpaste|Shampoo/Body wash|Fridge/Freezer|Washing machine/Dryer|Vacuum cleaner/Broom|Pillow/Blanket
|Sofa/Bed|Umbrella/Raincoat|Smartphone/Tablet|Laptop/Desktop PC|Headphones/Earbuds|Alarm clock/Wristwatch|Wallet/Purse
|Backpack/Suitcase|Glasses/Sunglasses|Sneakers/Boots|T-shirt/Hoodie|Jeans/Shorts|Bath/Shower|Microwave/Oven
|Fork/Spoon|Mug/Glass|Car/Motorcycle|Bus/Train|Bicycle/Scooter|Elevator/Escalator|Key/Password|Text message/Phone call
|Breakfast/Lunch|Nap/Night's sleep|Haircut/Hair dye|Mirror/Window|Calendar/Diary
""",
    "Hobbies": """
Soccer/Basketball|Baseball/Softball|Tennis/Badminton|Swimming/Surfing|Running/Hiking|Yoga/Pilates|Skiing/Snowboarding
|Bowling/Billiards|Karaoke/Concert|Video games/Board games|Chess/Checkers|Reading/Writing|Drawing/Painting|Guitar/Piano
|Camping/Picnic|Fishing/Hunting|Cooking/Baking|Photography/Filming videos|Dancing/Singing|Puzzle/Crossword|Gym/Home workout
|Shopping/Window shopping|Gardening/Houseplants|Knitting/Sewing|Magic tricks/Juggling
""",
    "Places & travel": """
Beach/Pool|Mountain/Hill|Airport/Train station|Hotel/Hostel|Museum/Art gallery|Zoo/Aquarium|Amusement park/Water park
|Library/Bookstore|Park/Garden|Mall/Supermarket|Restaurant/Cafe|Bakery/Pastry shop|Hospital/Clinic|Gym/Sports center
|Cinema/Theater|Camping site/Cabin|Island/Lake|Paris/London|New York/Los Angeles|Tokyo/Seoul|Hawaii/Bali
|Road trip/Cruise|Passport/Boarding pass
""",
    "Work & school": """
Teacher/Professor|Doctor/Nurse|Police officer/Firefighter|Chef/Baker|Pilot/Flight attendant|Lawyer/Judge|Actor/Singer
|Exam/Quiz|Homework/Assignment|Math/Science|History/Geography|Notebook/Textbook|Pencil/Pen|Eraser/Correction tape
|Classroom/Office|Meeting/Presentation|Email/Chat message|Boss/Coworker|Part-time job/Internship|Graduation/Entrance ceremony
|Summer break/Winter break|Lunch break/Recess|Salary/Bonus|Resume/Job interview
""",
    "Love & friends": """
First date/First kiss|Boyfriend/Best friend|Crush/Ex|Wedding/Engagement|Love letter/Confession|Valentine's Day/Anniversary
|Blind date/Dating app|Breakup/Fight|Ring/Necklace|Selfie/Couple photo|Sleepover/Party|Long-distance/Living together
""",
    "Seasons & events": """
Christmas/New Year's|Halloween/Thanksgiving|Birthday/Anniversary|Summer/Winter|Spring/Fall|Snow/Rain|Fireworks/Bonfire
|Easter/Valentine's Day|Wedding/Graduation|Santa Claus/Easter bunny|Snowman/Sandcastle|Christmas tree/Pumpkin lantern
|Vacation/Weekend|Barbecue/Picnic|Festival/Parade
""",
    "Animals": """
Dog/Cat|Lion/Tiger|Rabbit/Hamster|Elephant/Rhino|Giraffe/Zebra|Penguin/Seal|Dolphin/Whale|Shark/Crocodile|Eagle/Owl
|Horse/Donkey|Cow/Pig|Chicken/Duck|Monkey/Gorilla|Bear/Panda|Snake/Lizard|Frog/Turtle|Butterfly/Bee|Spider/Ant
|Fox/Wolf|Kangaroo/Koala|Parrot/Pigeon|Goldfish/Turtle|Squirrel/Raccoon
""",
    "Entertainment": """
Netflix/YouTube|Movie/TV series|Comedy/Horror movie|Superhero/Wizard|Zombie/Ghost|Vampire/Werewolf|Pirate/Ninja
|Robot/Alien|Cartoon/Anime|Instagram/TikTok|Podcast/Radio|Concert/Music festival|Magician/Clown|Princess/Queen
|Dragon/Dinosaur|Treasure/Gold|Spaceship/Rocket
""",
}
GUESS_CATEGORIES = ["Food", "Drinks", "Animals", "Daily life", "Hobbies", "Places & travel", "Seasons & events", "Entertainment"]

NG = {
    "ふつう": """
pizza|coffee|phone|money|dog|cat|movie|music|school|work|sleep|rain|summer|winter|birthday|party|game|friend|family
|food|water|car|train|beach|hotel|chocolate|ice cream|sushi|ramen|burger|cheese|egg|apple|banana|tea|beer|wine
|soccer|baseball|basketball|tennis|swimming|running|gym|diet|shopping|clothes|shoes|hair|makeup|selfie|camera
|Instagram|TikTok|YouTube|Netflix|internet|computer|email|password|homework|teacher|boss|meeting|weekend|Monday
|Friday|morning|night|tomorrow|yesterday|breakfast|lunch|dinner|snack|restaurant|kitchen|bathroom|bed|sofa|TV
|window|door|key|wallet|bag|umbrella|weather|sun|moon|star|sky|ocean|mountain|river|tree|flower|garden|park|zoo
|ghost|zombie|robot|alien|pirate|ninja|king|queen|princess|dragon|monster|hero|magic|dream|love|date|kiss|wedding
|baby|grandma|grandpa|mom|dad|brother|sister|cousin|neighbor|doctor|nurse|police|chef|singer|actor|idol|anime|manga
|karaoke|dance|guitar|piano|drum|song|book|story|news|secret|lie|joke|gift|cake|candy|cookie|bread|rice|noodles
|soup|salad|steak|chicken|fish|tomato|potato|carrot|onion|garlic|pepper|salt|sugar|spicy|sweet|cold|hot|tired|hungry
|happy|sad|angry|scary|funny|cute|beautiful|big|small|fast|slow|new|old|red|blue|green|yellow|black|white|pink
|Japan|Korea|China|America|France|Italy|Tokyo|Paris|London|New York|airport|passport|vacation|travel|picnic|camping
""",
    "ハード": """
yeah|okay|really|seriously|literally|actually|like|you know|I mean|so|well|right|cool|nice|wow|oh my god|no way
|basically|totally|honestly|anyway|whatever|exactly|sure|maybe|probably|kind of|sort of|I think|wait|hmm|uh|oops
|sorry|thanks|please|awesome|great|true|same|for real|definitely|absolutely|obviously|just|very|super|pretty much
""",
}

SPY_PLACES = """
Amusement park|Zoo|Aquarium|Movie theater|School|Hospital|Airport|Train station|Bank|Library|Art museum|History museum
|Police station|Fire station|Post office|Convenience store|Supermarket|Department store|Shopping mall|Restaurant|Pub|Cafe
|Ramen shop|Sushi bar|Barbecue restaurant|Diner|Hotel|Hot spring inn|Public bath|Campsite|Beach|Ski resort|Mountain hut
|Temple|Shrine|Church|Wedding hall|Courtroom|TV studio|Radio station|Factory|Farm|Fishing boat|Cruise ship|Airplane cabin
|Bullet train|Packed subway car|Bus|Space station|Submarine|Pirate ship|Castle|Ninja house|Haunted house|Circus|Theater
|Concert hall|Baseball stadium|Soccer stadium|Gym|Swimming pool|Fitness club|Hair salon|Dentist|Kindergarten|Karaoke box
|Internet cafe|Arcade|Bowling alley|Bakery|Flower shop|Bookstore|Police box|Deserted island|Antarctic base|Desert
|Jungle|Volcano|Prison|Parliament|Fish market|Secondhand bookstore|Fortune teller's booth|Magic show
""".replace("\n", "").split("|")

SYNC_PROMPTS = """
Name a red food.|Name a yellow fruit.|Name a pizza topping.|Something you buy at a convenience store?|Name a sandwich filling.
|Name a soup.|Name an ice cream flavor.|Name a type of cake.|Name a breakfast food.|Name a snack for movie night.
|Name a fruit.|Name a vegetable.|Name a round food.|Name a white food.|Name a sweet drink.|Something you eat in winter?
|Something you eat in summer?|Name a type of bread.|Name a fast-food chain.|Name a street food.|Name a pet.
|Name a farm animal.|Name a sea creature.|Name an animal that flies.|Name a big animal.|Name an insect.|Name a zoo animal.
|Name a color.|Name a shape.|Name a sport.|Name a ball game.|Name a winter sport.|Name a musical instrument.
|Name a board game.|Name a superhero.|Name a fairy-tale character.|Name a Disney-style princess.|Name a scary monster.
|Name a holiday.|Something you bring to the beach?|Something in a school bag?|Something in your fridge?
|Something in a bathroom?|Something in a kitchen?|Name a piece of furniture.|Name a kitchen tool.|Name an electronic device.
|Name a phone app.|Name a social network.|Something you do on a day off?|Name a hobby.|Name a job.
|Name something you do before bed.|Name something you do in the morning.|Name a country.|Name a big city.
|Name a place to go on a date.|Name a vacation spot.|Name a famous landmark.|Name a planet.|Name something in the sky.
|Name a type of weather.|Name a season.|Name a month.|Name a day of the week.|Name a number from 1 to 10.
|Name a lucky number.|Name a body part.|Name something you wear on your head.|Name a type of shoe.|Name something hot.
|Name something cold.|Name something soft.|Name something sharp.|Name something that smells good.|Name something loud.
|Name something you can't live without.|Name a gift idea.|Name a birthday party item.|Name a Christmas item.
|Name a Halloween costume.|Name something scary.|Name something cute.|Name something in a park.|Name a vehicle.
|Name something with wheels.|Name something with buttons.|Name a type of tree.|Name a flower.|Name a cartoon animal.
|Name a magic word.|Name a type of candy.|Name a dessert.|Name a spicy food.|Name a cheese.|Name a nut.
|Name something you collect.|Name a school subject.|Name something you find in an office.|Name a type of dance.
""".replace("\n", "").split("|")

_SO = """
wake up early|get rich|be popular in school|cook well|oversleep|cry at movies|get lost|be late
|survive on a desert island|keep a secret|be a great liar|become famous|get married first|be loved by animals
|sing well|be good at video games|forget their phone|handle spicy food|win a drinking contest|live to 100
|be scared of horror movies|be scary when angry|be the kindest|be the funniest|have a messy room|shop for hours
|become a CEO|be a great teacher|fall for a prank|win the lottery|be the luckiest|have the neatest handwriting
|spend the most time on their phone|know the latest trends|become obsessed with an idol|love traveling|stay home all weekend
|love the outdoors|have a sweet tooth|quit a diet|love working out|be the most athletic|give the best advice
|be a good listener|talk the most|be a born leader|be an airhead|laugh at anything|take great photos|be the most stylish
|go to karaoke alone|get confessed to the most|fall in love easily|plan the best surprise|forget an anniversary
|party until morning|get drunk first|miss the last train|lose their wallet|save the most money|spend a fortune
|live abroad|have a big family|spoil their pet|survive a zombie movie|turn into a zombie first|be a great detective
|be the villain in a movie|be a superhero|go to space|be scared of ghosts|be scared of bugs|be loved by kids
|eat the fastest|eat the most|be the most competitive|do things at their own pace|be the most reliable|worry too much
|be the most impatient|be the most laid-back|be the life of the party|sleep the messiest
""".replace("\n", "").split("|")
QUESTIONS = [f"Who here is most likely to {x}?" for x in _SO]

DRAW_WHO = """
A cat|A dog|A panda|A penguin|A gorilla|A rabbit|A pig|A frog|An octopus|A shark|A bear|A lion|A giraffe|An elephant
|A hamster|A chick|A turtle|A crocodile|A snake|An owl|A robot|An alien|A ghost|A ninja|A samurai|A king|A princess
|A wizard|A pirate|An astronaut|The principal|Grandma|Grandpa|A baby|Santa Claus|A snowman|A dinosaur|A zombie
|A vampire|A mermaid|An angel|A sumo wrestler|A firefighter|A doctor|A police officer|A chef|A pop idol|A detective
|A superhero|A banana|A rice ball|A slice of bread|A tomato|A broccoli|A fried egg|A fridge|A traffic light|A volcano
|A teacher|A unicorn
""".replace("\n", "").split("|")

DRAW_WHAT = """
surfing|flying through the sky|eating ramen|singing karaoke|on a packed train|having a barbecue on the moon
|relaxing in the bath|falling over while skiing|playing the guitar|running late after oversleeping|walking a tightrope
|celebrating a lottery win|crying because they're lost|in a dance battle|taking a selfie|panicking after dropping their phone
|dancing in the rain|fighting over sushi|lifting weights|catching a huge fish|screaming on a roller coaster
|confessing their love|in a snowball fight|slam-dunking a basketball|looking up at fireworks|being abducted by a UFO
|crossing a marathon finish line|fainting in a haunted house|throwing a pizza|blowing soap bubbles
|playing hide-and-seek in space|burning dinner|crying with a toothache|getting zero on a test|walking a dog
|with an umbrella blown inside out|working at a convenience store|riding a time machine|meditating under a waterfall
|bowling a strike|arguing on a Ferris wheel|blown away by a sneeze|doing yoga|nervous at a piano recital|buried in the sand
|carrying a giant cake|floating away on balloons|talking in their sleep|catching goldfish at a festival|sulking after losing a game
""".replace("\n", "").split("|")
