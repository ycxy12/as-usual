import { api } from "./http";
import type {
  Contact,
  ContactInput,
  Gift,
  GiftInput,
  PageResult,
} from "../types";
const contactPath = (id: string) => `/contacts/${encodeURIComponent(id)}`;
export const socialService = {
  contacts(page = 0, q = ""): Promise<PageResult<Contact>> {
    return api(`/contacts?page=${page}&size=20&q=${encodeURIComponent(q)}`);
  },
  contact(id: string): Promise<Contact> {
    return api(contactPath(id));
  },
  createContact(value: ContactInput): Promise<Contact> {
    return api("/contacts", "POST", value);
  },
  updateContact(id: string, value: ContactInput): Promise<Contact> {
    return api(contactPath(id), "PUT", value);
  },
  deleteContact(id: string): Promise<void> {
    return api(contactPath(id), "DELETE");
  },
  gifts(contactId: string, page = 0): Promise<PageResult<Gift>> {
    return api(`${contactPath(contactId)}/gifts?page=${page}&size=20`);
  },
  recent(): Promise<Gift[]> {
    return api("/gifts/recent?size=3");
  },
  gift(contactId: string, id: string): Promise<Gift> {
    return api(`${contactPath(contactId)}/gifts/${encodeURIComponent(id)}`);
  },
  createGift(contactId: string, value: GiftInput): Promise<Gift> {
    return api(`${contactPath(contactId)}/gifts`, "POST", value);
  },
  updateGift(contactId: string, id: string, value: GiftInput): Promise<Gift> {
    return api(
      `${contactPath(contactId)}/gifts/${encodeURIComponent(id)}`,
      "PUT",
      value,
    );
  },
  deleteGift(contactId: string, id: string): Promise<void> {
    return api(
      `${contactPath(contactId)}/gifts/${encodeURIComponent(id)}`,
      "DELETE",
    );
  },
};
