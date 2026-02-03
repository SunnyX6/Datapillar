export interface PricingFeature {
  text: string
  included: boolean
}

export interface PricingPlan {
  id: string
  name: string
  price: string
  description: string
  cta: string
  highlight?: boolean
  isCustom?: boolean
  features: PricingFeature[]
}

export const getPricingPlans = (t: (key: string, options?: Record<string, unknown>) => unknown) => {
  return t('pricing.plans', { returnObjects: true }) as PricingPlan[]
}
