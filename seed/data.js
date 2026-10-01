// Hand-curated, realistic-ish demo data. No API keys required — avatars come
// from pravatar.cc (deterministic per seed string) and photos from
// picsum.photos (deterministic per seed string, no auth needed).

export const FAKE_USERS = [
  {
    username: 'wanderlust_maya',
    displayName: 'Maya Chen',
    bio: 'Chasing sunsets and street food across 30 countries.'
  },
  {
    username: 'the_backpacker_jo',
    displayName: 'Jordan Reyes',
    bio: 'Budget travel tips and hidden gems. Currently: somewhere with wifi.'
  },
  {
    username: 'foodie_travels_kai',
    displayName: 'Kai Nakamura',
    bio: 'Eating my way through every city I visit.'
  },
  {
    username: 'slowtravel_amara',
    displayName: 'Amara Osei',
    bio: 'Slow travel advocate. Quality over quantity.'
  },
  {
    username: 'roadtrip_diego',
    displayName: 'Diego Martins',
    bio: 'Road trips, mountain views, and terrible navigation skills.'
  },
  {
    username: 'wanderer_sofia',
    displayName: 'Sofia Almeida',
    bio: 'Solo traveler documenting it all, one train ride at a time.'
  }
];

export function avatarUrlFor(username) {
  return `https://i.pravatar.cc/300?u=${encodeURIComponent(username)}`;
}

// One representative city per destination, with a realistic point pool per
// category. Coordinates are the real city center; each generated stop gets a
// small random jitter so points don't all land on the exact same pixel.
export const DESTINATIONS = [
  {
    name: 'Italy',
    city: 'Rome',
    lat: 41.9028,
    lng: 12.4964,
    hotels: ['Hotel Artemide', 'Hotel Raphael', 'The First Roma Arte'],
    sights: ['Colosseum', 'Roman Forum', 'Trevi Fountain', 'Pantheon', 'Vatican Museums', 'Piazza Navona'],
    food: ['Trattoria da Enzo', 'Pizzarium', 'Roscioli', 'Gelateria del Teatro'],
    misc: [
      { name: 'Termini Station', category: 'transport' },
      { name: "Campo de' Fiori Market", category: 'other' }
    ]
  },
  {
    name: 'Japan',
    city: 'Tokyo',
    lat: 35.6762,
    lng: 139.6503,
    hotels: ['Park Hotel Tokyo', 'Shinjuku Granbell Hotel', 'Hotel Gracery Shinjuku'],
    sights: ['Senso-ji Temple', 'Shibuya Crossing', 'Meiji Shrine', 'Tokyo Tower', 'Tsukiji Outer Market', 'teamLab Planets'],
    food: ['Ichiran Ramen Shibuya', 'Sushi Dai', 'Ginza Kyubey'],
    misc: [
      { name: 'Shinjuku Station', category: 'transport' },
      { name: 'Akihabara Electric Town', category: 'other' }
    ]
  },
  {
    name: 'Thailand',
    city: 'Bangkok',
    lat: 13.7563,
    lng: 100.5018,
    hotels: ['Mandarin Oriental Bangkok', 'Riva Surya Bangkok', 'Chatrium Hotel Riverside'],
    sights: ['Grand Palace', 'Wat Arun', 'Wat Pho', 'Chatuchak Weekend Market', 'Jim Thompson House'],
    food: ['Thip Samai Pad Thai', 'Jay Fai', 'Chinatown Street Food'],
    misc: [
      { name: 'Hua Lamphong Station', category: 'transport' },
      { name: 'Asiatique The Riverfront', category: 'other' }
    ]
  },
  {
    name: 'Portugal',
    city: 'Lisbon',
    lat: 38.7223,
    lng: -9.1393,
    hotels: ['Pestana Palace Lisboa', 'The Lumiares Hotel', 'Memmo Alfama'],
    sights: ['Belem Tower', 'Jeronimos Monastery', 'Sao Jorge Castle', 'Alfama District', 'Tram 28 Route'],
    food: ['Pasteis de Belem', 'Time Out Market Lisboa', 'Cervejaria Ramiro'],
    misc: [
      { name: 'Rossio Station', category: 'transport' },
      { name: 'LX Factory', category: 'other' }
    ]
  },
  {
    name: 'Mexico',
    city: 'Mexico City',
    lat: 19.4326,
    lng: -99.1332,
    hotels: ['Hotel Carlota', 'Casa Polanco', 'Downtown Mexico Hotel'],
    sights: ['Zocalo', 'Frida Kahlo Museum', 'Chapultepec Castle', 'Teotihuacan Pyramids', 'Palacio de Bellas Artes'],
    food: ['El Cardenal', 'Pujol', 'Mercado de San Juan'],
    misc: [
      { name: 'Metro Bellas Artes', category: 'transport' },
      { name: 'Coyoacan Market', category: 'other' }
    ]
  }
];

export const BUDGET_TAGS = ['budget', 'mid_range', 'luxury'];
export const SEASON_TAGS = ['spring', 'summer', 'fall', 'winter'];
export const TRIP_NOUNS = ['Adventure', 'Getaway', 'Escape', 'Journey', 'Discovery', 'Trip'];

// Practical, category-appropriate tips. Not every stop gets one (see
// TIP_PROBABILITY) so tips read as earned advice rather than filler.
export const TIP_PROBABILITY = 0.6;
export const TIPS_BY_CATEGORY = {
  hotel: [
    'Ask for a room on a higher floor - street noise carries late into the night.',
    'Breakfast is worth it here; skip the cafes nearby.',
    'Luggage storage is free after checkout, so plan a last afternoon out.'
  ],
  food: [
    'Go right at opening - the line is triple the length by noon.',
    'Cash only, and they run out of the specials early.',
    'Order at the counter first, then grab a table - nobody comes to you.',
    'Portions are big; one plate is plenty for two.'
  ],
  sight: [
    'Book tickets online the day before to skip the ticket line.',
    'Arrive before 9am for photos without the crowds.',
    'The late-afternoon light is best - come an hour before sunset.',
    'Wear comfortable shoes; the ground is uneven and there is a lot of walking.'
  ],
  transport: [
    'Buy a reloadable transit card at the machine - singles cost more.',
    'Avoid rush hour between 8 and 9:30am; trains are packed.',
    'Download the offline map before you go - signal is patchy underground.'
  ],
  other: [
    'Most stalls open around 10am; weekends are much busier.',
    'Bring small bills - many vendors cannot break large notes.',
    'Take a slow lap first before buying anything; prices vary a lot.'
  ]
};
