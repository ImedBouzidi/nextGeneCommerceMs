export interface Product {
    id: number;
    name: string;
    description: string;
    price: number;
    image: string;
    quantity: number;
    images?: string[];
    rate?: number; // average rating (0-5)
    review?: string[];
}
