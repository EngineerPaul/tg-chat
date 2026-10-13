"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { getAccess } from "@/lib/storage";

/**
 * Корень сайта: в чат или на вход.
 */
export default function HomePage() {
  const router = useRouter();
  useEffect(() => {
    router.replace(getAccess() ? "/chat" : "/login");
  }, [router]);
  return null;
}
