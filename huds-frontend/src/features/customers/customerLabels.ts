import type { CustomerType } from '../../api/types'

/** What the user reads for every customer type. */
export const CUSTOMER_TYPE_LABELS: Record<CustomerType, string> = {
  RETAIL_COD: 'Retail (pays on the spot)',
  PROPERTY_LANDLORD: 'Landlord / property manager',
  THIRD_PARTY_WARRANTY: 'Third-party warranty company',
  OEM_FACTORY: 'Manufacturer (OEM)',
}

export const CUSTOMER_TYPES = Object.keys(CUSTOMER_TYPE_LABELS) as CustomerType[]

export function customerTypeLabel(type: CustomerType | undefined): string {
  return type ? (CUSTOMER_TYPE_LABELS[type] ?? type) : '-'
}
