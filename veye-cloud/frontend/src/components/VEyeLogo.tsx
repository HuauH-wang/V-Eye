type Props = {
  size?: number;
  className?: string;
  showWordmark?: boolean;
};

/** V-Eye neo-brutalism mark: eye + V pupil */
export default function VEyeLogo({ size = 42, className = "", showWordmark = false }: Props) {
  const h = showWordmark ? size * 1.35 : size;

  return (
    <svg
      className={className}
      width={size}
      height={h}
      viewBox={showWordmark ? "0 0 48 64" : "0 0 48 48"}
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      role="img"
      aria-label="V-Eye"
    >
      <title>V-Eye</title>
      <g filter="url(#veye-shadow)">
        <rect x="2" y="2" width="44" height="44" fill="#E8D5F2" stroke="#0A0A0A" strokeWidth="3" />
        <path
          d="M24 14c-7.5 0-13.5 6-16.5 10.5 3 4.5 9 10.5 16.5 10.5S37 29 40 24.5C37 20 31 14 24 14z"
          fill="#fff"
          stroke="#0A0A0A"
          strokeWidth="2.5"
          strokeLinejoin="round"
        />
        <circle cx="24" cy="24.5" r="7" fill="#FFE566" stroke="#0A0A0A" strokeWidth="2.5" />
        <path d="M21 28 L24 19 L27 28 Z" fill="#0A0A0A" />
        <circle cx="26.5" cy="22.5" r="1.5" fill="#fff" />
      </g>
      {showWordmark ? (
        <text
          x="24"
          y="58"
          textAnchor="middle"
          fill="#0A0A0A"
          fontFamily="Segoe UI, system-ui, sans-serif"
          fontSize="8"
          fontWeight="900"
          letterSpacing="1.2"
        >
          V-EYE
        </text>
      ) : null}
      <defs>
        <filter id="veye-shadow" x="0" y="0" width="52" height="52" filterUnits="userSpaceOnUse">
          <feOffset dx="2" dy="2" />
          <feFlood floodColor="#0A0A0A" />
          <feComposite in2="SourceAlpha" operator="in" />
          <feMerge>
            <feMergeNode />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>
    </svg>
  );
}
