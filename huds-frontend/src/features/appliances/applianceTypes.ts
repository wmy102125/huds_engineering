/** The appliance types of the server (ApplianceModelType). The value is what is stored; the label is what the user reads. */
export const APPLIANCE_TYPES = [
  { value: 'WASHER', label: 'Washer' },
  { value: 'DRYER', label: 'Dryer' },
  { value: 'WASHER_DRYER_COMBO', label: 'Washer-dryer combo' },
  { value: 'REFRIGERATOR', label: 'Refrigerator' },
  { value: 'FREEZER', label: 'Freezer' },
  { value: 'DISHWASHER', label: 'Dishwasher' },
  { value: 'RANGE', label: 'Range' },
  { value: 'COOKTOP', label: 'Cooktop' },
  { value: 'WALL_OVEN', label: 'Wall oven' },
  { value: 'MICROWAVE', label: 'Microwave' },
  { value: 'RANGE_HOOD', label: 'Range hood' },
  { value: 'AIR_CONDITIONER', label: 'Air conditioner' },
  { value: 'DEHUMIDIFIER', label: 'Dehumidifier' },
  { value: 'ICE_MAKER', label: 'Ice maker' },
  { value: 'WINE_COOLER', label: 'Wine cooler' },
  { value: 'TRASH_COMPACTOR', label: 'Trash compactor' },
  { value: 'OTHER', label: 'Other' },
] as const

export function applianceTypeLabel(value: string): string {
  return APPLIANCE_TYPES.find((type) => type.value === value)?.label ?? value
}
