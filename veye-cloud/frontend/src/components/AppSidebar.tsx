import {
  AlertTriangle,
  FileText,
  LayoutGrid,
  Leaf,
  Map,
  PersonStanding,
  Settings,
  Users,
  type LucideIcon,
} from "lucide-react";
import type { TabId } from "../lib/types";
import BrandHeader from "./BrandHeader";

const NAV: { id: TabId; label: string; icon: LucideIcon }[] = [
  { id: "library", label: "图鉴", icon: LayoutGrid },
  { id: "map", label: "地图", icon: Map },
  { id: "team", label: "组队", icon: Users },
  { id: "report", label: "工作报告", icon: FileText },
  { id: "environment", label: "环境", icon: Leaf },
  { id: "motion", label: "运动", icon: PersonStanding },
  { id: "sos", label: "SOS", icon: AlertTriangle },
  { id: "settings", label: "设置", icon: Settings },
];

type Props = {
  tab: TabId;
  onTabChange: (tab: TabId) => void;
};

export default function AppSidebar({ tab, onTabChange }: Props) {
  return (
    <aside className="app-sidebar neo-card" aria-label="功能导航">
      <BrandHeader className="sidebar-brand" />

      <nav className="sidebar-nav">
        {NAV.map((item) => {
          const Icon = item.icon;
          return (
            <button
              key={item.id}
              type="button"
              className={`sidebar-nav-btn${tab === item.id ? " active" : ""}`}
              onClick={() => onTabChange(item.id)}
            >
              <span className="sidebar-nav-icon" aria-hidden>
                <Icon size={18} strokeWidth={2.2} />
              </span>
              <span className="sidebar-nav-label">{item.label}</span>
            </button>
          );
        })}
      </nav>
    </aside>
  );
}
