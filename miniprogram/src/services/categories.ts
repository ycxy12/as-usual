import { profileService } from "./profile";
import type { CategoryModule } from "../types";

export async function categoryNames(
  module: CategoryModule,
  fallback: readonly string[],
): Promise<string[]> {
  try {
    const values = await profileService.categories(module);
    return values.length ? values.map((x) => x.name) : [...fallback];
  } catch {
    return [...fallback];
  }
}
